package com.morak.feature.point.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.point.application.dto.PointChangeResult;
import com.morak.feature.point.application.dto.PointLedgerResult;
import com.morak.feature.point.domain.entity.PointGrantReason;
import com.morak.feature.point.domain.entity.PointTransaction;
import com.morak.feature.point.domain.exception.PointErrorCode;
import com.morak.feature.point.domain.repository.PointAccountRepository;
import com.morak.feature.point.domain.repository.PointAccountRepository.BalanceChange;
import com.morak.feature.point.domain.repository.PointTransactionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 잔액 변경과 원장 조회. 잔액 변경은 챌린지·참가자 도메인이 호출한다.
 * 잔액 변경과 원장 기록은 한 트랜잭션으로 묶어, 둘 중 하나만 반영되는 일이 없게 한다.
 */
@Service
public class PointService {

	public static final int DEFAULT_LIMIT = 20;
	public static final int MAX_LIMIT = 100;

	private final PointAccountRepository accountRepository;
	private final PointTransactionRepository transactionRepository;
	private final Clock clock;

	public PointService(
			PointAccountRepository accountRepository, PointTransactionRepository transactionRepository, Clock clock) {
		this.accountRepository = accountRepository;
		this.transactionRepository = transactionRepository;
		this.clock = clock;
	}

	/** 포인트 지급. 같은 idempotencyKey로 다시 호출하면 잔액을 바꾸지 않고 처음 결과를 돌려준다. */
	@Transactional
	public PointChangeResult grant(Long userId, PointGrantReason reason, long amount, String idempotencyKey) {
		return change(
				amount,
				idempotencyKey,
				now -> accountRepository.increase(userId, amount, now),
				(change, now) -> PointTransaction.grant(
						change.accountId(), reason, amount, change.balanceAfter(), idempotencyKey, now));
	}

	/**
	 * 보증금 환급. 참가자당 한 번만 반영된다 (키: REFUND:{participationId}).
	 * challengeTitle은 내역 표시용으로 거래 당시 이름을 그대로 남긴다.
	 */
	@Transactional
	public PointChangeResult refund(Long userId, Long participationId, String challengeTitle, long amount) {
		return change(
				amount,
				"REFUND:" + participationId,
				now -> accountRepository.increase(userId, amount, now),
				(change, now) -> PointTransaction.refund(
						change.accountId(), participationId, challengeTitle, amount, change.balanceAfter(), now));
	}

	/**
	 * 보증금 납부. amount는 참가자의 deposit_amount를 양수로 넘긴다. 참가자당 한 번만 반영된다 (키: DEPOSIT:{participationId}).
	 * 잔액이 부족하거나 계정이 없으면 INSUFFICIENT_BALANCE. challengeTitle은 환급과 같다.
	 */
	@Transactional
	public PointChangeResult deposit(Long userId, Long participationId, String challengeTitle, long amount) {
		return change(
				amount,
				"DEPOSIT:" + participationId,
				now -> accountRepository.decrease(userId, amount, now)
						.orElseThrow(() -> new BusinessException(PointErrorCode.INSUFFICIENT_BALANCE)),
				(change, now) -> PointTransaction.deposit(
						change.accountId(), participationId, challengeTitle, amount, change.balanceAfter(), now));
	}

	/**
	 * 현재 잔액과 거래 내역 (id 내림차순 = 최신순). before가 있으면 그 id보다 오래된 거래부터.
	 * REPEATABLE READ로 두 조회가 같은 시점을 보게 해, 그 사이 거래가 커밋돼도 잔액과 내역이 어긋나지 않는다.
	 * 계정이 없으면 잔액 0, 내역 없음.
	 */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public PointLedgerResult getLedger(Long userId, Long before, Integer limit) {
		int size = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
		return accountRepository.findByUserId(userId)
				.map(account -> {
					// 한 개 더 읽어 다음 페이지가 있는지 판단한다.
					List<PointTransaction> rows = transactionRepository.findByAccountId(account.getId(), before, size + 1);
					boolean hasNext = rows.size() > size;
					List<PointTransaction> page = hasNext ? rows.subList(0, size) : rows;
					Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
					return PointLedgerResult.of(account.getBalance(), page, nextCursor);
				})
				.orElseGet(PointLedgerResult::empty);
	}

	/**
	 * 같은 키로 동시에 들어오면 키 단위 락으로 한 줄로 세운다. 늦게 온 쪽은 앞선 트랜잭션이 끝날 때까지 기다렸다가
	 * 원장에서 앞선 거래를 찾아 그 결과를 돌려준다. 앞선 트랜잭션이 롤백됐으면 처음부터 새로 처리한다.
	 * 원장의 idempotency_key 유니크 제약은 마지막 안전장치로 남겨 둔다.
	 */
	private PointChangeResult change(
			long amount,
			String idempotencyKey,
			Function<LocalDateTime, BalanceChange> changeBalance,
			BiFunction<BalanceChange, LocalDateTime, PointTransaction> toTransaction) {
		if (amount <= 0) {
			throw new BusinessException(PointErrorCode.INVALID_AMOUNT);
		}
		transactionRepository.lockIdempotencyKey(idempotencyKey);
		Optional<PointTransaction> processed = transactionRepository.findByIdempotencyKey(idempotencyKey);
		if (processed.isPresent()) {
			return PointChangeResult.from(processed.get());
		}
		// PostgreSQL은 마이크로초까지만 저장하므로 미리 잘라 응답과 DB 값을 맞춘다.
		LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
		BalanceChange change = changeBalance.apply(now);
		return PointChangeResult.from(transactionRepository.save(toTransaction.apply(change, now)));
	}
}
