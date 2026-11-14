package com.morak.feature.point.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.point.application.dto.PointChangeResult;
import com.morak.feature.point.application.dto.PointLedgerResult;
import com.morak.feature.point.domain.entity.PointAccount;
import com.morak.feature.point.domain.entity.PointGrantReason;
import com.morak.feature.point.domain.entity.PointTransaction;
import com.morak.feature.point.domain.entity.PointTransactionType;
import com.morak.feature.point.domain.exception.PointErrorCode;
import com.morak.feature.point.domain.repository.PointAccountRepository;
import com.morak.feature.point.domain.repository.PointTransactionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 잔액 변경과 원장 조회. 잔액 변경은 챌린지·참가자 도메인이 호출한다.
 * 잔액 변경과 원장 기록은 한 트랜잭션으로 묶어, 둘 중 하나만 반영되는 일이 없게 한다.
 *
 * 거절할 때는 판단 근거(요청 값, 당시 잔액 등)를 BusinessException의 detail에 담는다. 로그는 GlobalExceptionHandler가 남긴다.
 * 성공한 잔액 변경은 원장 자체가 기록이므로 따로 로그를 남기지 않는다.
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
		Command command = new Command(PointTransactionType.GRANT, userId, amount, idempotencyKey);
		require(command, "userId", userId);
		require(command, "reason", reason);
		validateIdempotencyKey(command);
		return change(
				command,
				(accountId, now) -> accountRepository.increase(accountId, amount, now),
				(accountId, balanceAfter, now) -> PointTransaction.grant(
						accountId, reason, amount, balanceAfter, idempotencyKey, now));
	}

	/**
	 * 보증금 환급. 참가자당 한 번만 반영된다 (키: REFUND:{participationId}).
	 * 같은 계정에 그 참가자의 보증금 납부 내역이 있어야 하고, 환급 금액은 납부한 보증금 이하여야 한다.
	 * challengeTitle은 내역 표시용으로 거래 당시 이름을 그대로 남긴다.
	 */
	@Transactional
	public PointChangeResult refund(Long userId, Long participationId, String challengeTitle, long amount) {
		Command command = new Command(PointTransactionType.REFUND, userId, amount, "REFUND:" + participationId);
		require(command, "userId", userId);
		require(command, "participationId", participationId);
		validateChallengeTitle(command, challengeTitle);
		return change(
				command,
				(accountId, now) -> {
					requireRefundableDeposit(command, accountId, participationId);
					return accountRepository.increase(accountId, amount, now);
				},
				(accountId, balanceAfter, now) -> PointTransaction.refund(
						accountId, participationId, challengeTitle, amount, balanceAfter, now));
	}

	/**
	 * 보증금 납부. amount는 참가자의 deposit_amount를 양수로 넘긴다. 참가자당 한 번만 반영된다 (키: DEPOSIT:{participationId}).
	 * 잔액이 부족하면 INSUFFICIENT_BALANCE. 계정이 없으면 잔액 0으로 보고 같은 예외다. challengeTitle은 환급과 같다.
	 */
	@Transactional
	public PointChangeResult deposit(Long userId, Long participationId, String challengeTitle, long amount) {
		Command command = new Command(PointTransactionType.DEPOSIT, userId, amount, "DEPOSIT:" + participationId);
		require(command, "userId", userId);
		require(command, "participationId", participationId);
		validateChallengeTitle(command, challengeTitle);
		return change(
				command,
				(accountId, now) -> accountRepository.decrease(accountId, amount, now)
						.orElseThrow(() -> rejected(PointErrorCode.INSUFFICIENT_BALANCE, command.describe()
								+ " accountId=" + accountId + " balance=" + currentBalance(command.userId()))),
				(accountId, balanceAfter, now) -> PointTransaction.deposit(
						accountId, participationId, challengeTitle, amount, balanceAfter, now));
	}

	/**
	 * 현재 잔액과 거래 내역 (id 내림차순 = 최신순). before가 있으면 그 id보다 오래된 거래부터.
	 * REPEATABLE READ로 두 조회가 같은 시점을 보게 해, 그 사이 거래가 커밋돼도 잔액과 내역이 어긋나지 않는다.
	 * 계정이 없으면 잔액 0, 내역 없음. limit은 1~100으로 맞춘다.
	 */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public PointLedgerResult getLedger(Long userId, Long before, Integer limit) {
		if (before != null && before <= 0) {
			throw rejected(PointErrorCode.INVALID_CURSOR, "userId=" + userId + " before=" + before);
		}
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
	 * 계정 행을 먼저 잠가 같은 유저의 잔액 변경을 한 줄로 세운다. 늦게 온 쪽은 앞선 트랜잭션이 끝날 때까지 기다렸다가
	 * 원장에서 같은 키의 거래를 찾으면 그 결과를 돌려준다. 앞선 트랜잭션이 롤백됐으면 처음부터 새로 처리한다.
	 * 원장의 idempotency_key 유니크 제약은 마지막 안전장치로 남겨 둔다.
	 * 실패하면 트랜잭션째 롤백되므로, 차감 실패로 새로 만든 빈 계정도 남지 않는다.
	 */
	private PointChangeResult change(Command command, BalanceUpdate changeBalance, LedgerEntry toTransaction) {
		if (command.amount() <= 0) {
			throw rejected(PointErrorCode.INVALID_AMOUNT, command.describe());
		}
		// PostgreSQL은 마이크로초까지만 저장하므로 미리 잘라 응답과 DB 값을 맞춘다.
		LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
		Long accountId = accountRepository.lockForUpdate(command.userId(), now);
		Optional<PointTransaction> processed = transactionRepository.findByIdempotencyKey(command.idempotencyKey());
		if (processed.isPresent()) {
			return replay(command, accountId, processed.get());
		}
		long balanceAfter = changeBalance.apply(accountId, now);
		return PointChangeResult.from(save(command, toTransaction.create(accountId, balanceAfter, now)));
	}

	/**
	 * 이미 처리한 키면 처음 결과를 돌려준다. 다만 계정·종류·금액이 다르면 호출한 쪽이 키를 잘못 만든 것이므로
	 * 처음 결과를 돌려주지 않고 거절한다 (예: 다른 유저가 같은 participationId로 환급 요청).
	 */
	private PointChangeResult replay(Command command, Long accountId, PointTransaction processed) {
		boolean sameRequest = processed.getAccountId().equals(accountId)
				&& processed.getType() == command.type()
				&& processed.getAmount() == command.signedAmount();
		if (!sameRequest) {
			throw rejected(PointErrorCode.IDEMPOTENCY_KEY_CONFLICT, command.describe()
					+ " accountId=" + accountId
					+ " | existing transactionId=" + processed.getId()
					+ " accountId=" + processed.getAccountId()
					+ " type=" + processed.getType()
					+ " signedAmount=" + processed.getAmount());
		}
		return PointChangeResult.from(processed);
	}

	/**
	 * 계정 행 락은 같은 유저끼리만 줄 세우므로, 다른 유저가 같은 키로 동시에 들어오면 유니크 제약에 걸린다.
	 * 정상 흐름에서는 생기지 않는 상황이라 원인 예외를 함께 담아, DB 예외 대신 키 충돌로 알린다. 트랜잭션은 롤백된다.
	 */
	private PointTransaction save(Command command, PointTransaction transaction) {
		try {
			return transactionRepository.save(transaction);
		} catch (DataIntegrityViolationException exception) {
			throw new BusinessException(PointErrorCode.IDEMPOTENCY_KEY_CONFLICT,
					"같은 idempotencyKey가 동시에 저장됨 " + command.describe() + " accountId=" + transaction.getAccountId(),
					exception);
		}
	}

	private void requireRefundableDeposit(Command command, Long accountId, Long participationId) {
		PointTransaction deposit = transactionRepository.findByIdempotencyKey("DEPOSIT:" + participationId)
				.orElseThrow(() -> rejected(PointErrorCode.DEPOSIT_NOT_FOUND,
						command.describe() + " accountId=" + accountId + " participationId=" + participationId));
		if (!deposit.getAccountId().equals(accountId)) {
			throw rejected(PointErrorCode.DEPOSIT_NOT_FOUND, command.describe()
					+ " accountId=" + accountId + " participationId=" + participationId
					+ " | deposit transactionId=" + deposit.getId() + " belongs to accountId=" + deposit.getAccountId());
		}
		long depositAmount = -deposit.getAmount();
		if (command.amount() > depositAmount) {
			throw rejected(PointErrorCode.REFUND_EXCEEDS_DEPOSIT, command.describe()
					+ " accountId=" + accountId + " depositTransactionId=" + deposit.getId()
					+ " depositAmount=" + depositAmount);
		}
	}

	private static void require(Command command, String name, Object value) {
		if (value == null) {
			throw rejected(PointErrorCode.MISSING_REQUIRED_VALUE, command.describe() + " missing=" + name);
		}
	}

	private static void validateIdempotencyKey(Command command) {
		String key = command.idempotencyKey();
		if (key == null || key.isBlank() || key.length() > PointTransaction.MAX_IDEMPOTENCY_KEY_LENGTH) {
			throw rejected(PointErrorCode.INVALID_IDEMPOTENCY_KEY, command.describe()
					+ " keyLength=" + (key == null ? "null" : key.length()));
		}
	}

	private static void validateChallengeTitle(Command command, String challengeTitle) {
		if (challengeTitle == null
				|| challengeTitle.isBlank()
				|| challengeTitle.length() > PointTransaction.MAX_CHALLENGE_TITLE_LENGTH) {
			throw rejected(PointErrorCode.INVALID_CHALLENGE_TITLE, command.describe()
					+ " challengeTitleLength=" + (challengeTitle == null ? "null" : challengeTitle.length()));
		}
	}

	/** 잔액 부족 로그용. 계정 행을 잠근 상태라 차감 시도 직전 잔액과 같다. */
	private long currentBalance(Long userId) {
		return accountRepository.findByUserId(userId).map(PointAccount::getBalance).orElse(0L);
	}

	private static BusinessException rejected(PointErrorCode errorCode, String detail) {
		return new BusinessException(errorCode, detail);
	}

	/** 로그와 중복 요청 비교에 쓰는 요청 요약. amount는 호출한 쪽이 넘긴 양수 금액이다. */
	private record Command(PointTransactionType type, Long userId, long amount, String idempotencyKey) {

		long signedAmount() {
			return type == PointTransactionType.DEPOSIT ? -amount : amount;
		}

		String describe() {
			return "type=" + type + " userId=" + userId + " amount=" + amount + " idempotencyKey=" + idempotencyKey;
		}
	}

	@FunctionalInterface
	private interface BalanceUpdate {
		long apply(Long accountId, LocalDateTime now);
	}

	@FunctionalInterface
	private interface LedgerEntry {
		PointTransaction create(Long accountId, long balanceAfter, LocalDateTime now);
	}
}
