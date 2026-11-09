package com.morak.feature.point.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PointServiceTest {

	private static final Long USER = 10L;
	private static final String TITLE = "매일 아침 러닝";

	private final FakeAccountRepository accounts = new FakeAccountRepository();
	private final FakeTransactionRepository transactions = new FakeTransactionRepository();
	private final PointService service = new PointService(
			accounts, transactions, Clock.fixed(Instant.parse("2026-11-09T03:00:00.123456789Z"), ZoneOffset.UTC));

	@Test
	void grantIncreasesBalanceAndRecordsLedger() {
		PointChangeResult result = service.grant(USER, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:10");

		assertEquals(10_000, result.amount());
		assertEquals(10_000, result.balanceAfter());
		PointTransaction saved = transactions.saved.get(0);
		assertEquals(PointTransactionType.GRANT, saved.getType());
		assertEquals(PointGrantReason.SIGNUP_GIFT, saved.getReason());
		assertEquals(accounts.accountIdOf(USER), saved.getAccountId());
		assertEquals("SIGNUP_GIFT:10", saved.getIdempotencyKey());
		assertNull(saved.getParticipationId());
	}

	@Test
	void refundAddsToExistingBalanceWithParticipationKey() {
		service.grant(USER, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:10");

		PointChangeResult result = service.refund(USER, 7L, TITLE, 3_000);

		assertEquals(3_000, result.amount());
		assertEquals(13_000, result.balanceAfter());
		PointTransaction saved = transactions.saved.get(1);
		assertEquals(PointTransactionType.REFUND, saved.getType());
		assertEquals(7L, saved.getParticipationId());
		assertEquals("REFUND:7", saved.getIdempotencyKey());
		assertEquals(TITLE, saved.getChallengeTitle());
	}

	@Test
	void sameIdempotencyKeyIsAppliedOnlyOnce() {
		PointChangeResult first = service.refund(USER, 7L, TITLE, 3_000);
		PointChangeResult second = service.refund(USER, 7L, TITLE, 3_000);

		assertEquals(first, second);
		assertEquals(3_000, accounts.balanceOf(USER));
		assertEquals(1, transactions.saved.size());
	}

	@Test
	void depositDecreasesBalanceAndRecordsNegativeAmount() {
		service.grant(USER, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:10");

		PointChangeResult result = service.deposit(USER, 7L, TITLE, 3_000);

		assertEquals(-3_000, result.amount());
		assertEquals(7_000, result.balanceAfter());
		PointTransaction saved = transactions.saved.get(1);
		assertEquals(PointTransactionType.DEPOSIT, saved.getType());
		assertEquals(7L, saved.getParticipationId());
		assertEquals("DEPOSIT:7", saved.getIdempotencyKey());
		assertEquals(TITLE, saved.getChallengeTitle());
	}

	@Test
	void depositCanUseWholeBalance() {
		service.grant(USER, PointGrantReason.SIGNUP_GIFT, 3_000, "SIGNUP_GIFT:10");

		assertEquals(0, service.deposit(USER, 7L, TITLE, 3_000).balanceAfter());
	}

	@Test
	void depositOverBalanceIsRejectedWithoutChanges() {
		service.grant(USER, PointGrantReason.SIGNUP_GIFT, 2_999, "SIGNUP_GIFT:10");

		BusinessException exception = assertThrows(BusinessException.class, () -> service.deposit(USER, 7L, TITLE, 3_000));

		assertEquals(PointErrorCode.INSUFFICIENT_BALANCE, exception.getErrorCode());
		assertEquals(2_999, accounts.balanceOf(USER));
		assertEquals(1, transactions.saved.size());
	}

	@Test
	void depositWithoutAccountIsInsufficientBalance() {
		BusinessException exception = assertThrows(BusinessException.class, () -> service.deposit(USER, 7L, TITLE, 3_000));

		assertEquals(PointErrorCode.INSUFFICIENT_BALANCE, exception.getErrorCode());
	}

	@Test
	void sameParticipationDepositIsAppliedOnlyOnce() {
		service.grant(USER, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:10");

		PointChangeResult first = service.deposit(USER, 7L, TITLE, 3_000);
		PointChangeResult second = service.deposit(USER, 7L, TITLE, 3_000);

		assertEquals(first, second);
		assertEquals(7_000, accounts.balanceOf(USER));
	}

	@Test
	void nonPositiveAmountIsRejected() {
		BusinessException zero = assertThrows(
				BusinessException.class, () -> service.grant(USER, PointGrantReason.SIGNUP_GIFT, 0, "k"));
		BusinessException negative = assertThrows(BusinessException.class, () -> service.refund(USER, 7L, TITLE, -1));
		BusinessException depositZero = assertThrows(BusinessException.class, () -> service.deposit(USER, 7L, TITLE, 0));

		assertEquals(PointErrorCode.INVALID_AMOUNT, zero.getErrorCode());
		assertEquals(PointErrorCode.INVALID_AMOUNT, negative.getErrorCode());
		assertEquals(PointErrorCode.INVALID_AMOUNT, depositZero.getErrorCode());
		assertEquals(0, transactions.saved.size());
	}

	@Test
	void createdAtIsTruncatedToMicroseconds() {
		service.grant(USER, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:10");

		assertEquals(LocalDateTime.of(2026, 11, 9, 3, 0, 0, 123_456_000), transactions.saved.get(0).getCreatedAt());
	}

	@Test
	void ledgerShowsBalanceAndTransactionsNewestFirst() {
		service.grant(USER, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:10");
		service.deposit(USER, 7L, TITLE, 3_000);

		PointLedgerResult ledger = service.getLedger(USER, null, null);

		assertEquals(7_000, ledger.balance());
		assertEquals(2, ledger.transactions().size());
		PointLedgerResult.Entry latest = ledger.transactions().get(0);
		assertEquals(PointTransactionType.DEPOSIT, latest.type());
		assertEquals(TITLE, latest.challengeTitle());
		assertEquals(-3_000, latest.amount());
		assertEquals(7_000, latest.balanceAfter());
		assertEquals(Instant.parse("2026-11-09T03:00:00.123456Z"), latest.createdAt());
		assertEquals(PointGrantReason.SIGNUP_GIFT, ledger.transactions().get(1).reason());
		assertNull(ledger.nextCursor());
	}

	@Test
	void ledgerPagesWithCursor() {
		service.grant(USER, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:10");
		for (long participation = 1; participation <= 4; participation++) {
			service.deposit(USER, participation, TITLE, 1_000);
		}

		PointLedgerResult first = service.getLedger(USER, null, 2);
		PointLedgerResult second = service.getLedger(USER, first.nextCursor(), 2);
		PointLedgerResult last = service.getLedger(USER, second.nextCursor(), 2);

		assertEquals(List.of(5L, 4L), ids(first));
		assertEquals(4L, first.nextCursor());
		assertEquals(List.of(3L, 2L), ids(second));
		assertEquals(List.of(1L), ids(last));
		assertNull(last.nextCursor());
	}

	@Test
	void ledgerWithoutAccountIsEmpty() {
		PointLedgerResult ledger = service.getLedger(USER, null, null);

		assertEquals(0, ledger.balance());
		assertEquals(List.of(), ledger.transactions());
		assertNull(ledger.nextCursor());
	}

	@Test
	void ledgerLimitIsClampedToRange() {
		service.grant(USER, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:10");
		service.deposit(USER, 7L, TITLE, 1_000);

		assertEquals(1, service.getLedger(USER, null, 0).transactions().size());
		assertEquals(2, service.getLedger(USER, null, 1_000).transactions().size());
	}

	private static List<Long> ids(PointLedgerResult ledger) {
		return ledger.transactions().stream().map(PointLedgerResult.Entry::id).toList();
	}

	private static class FakeAccountRepository implements PointAccountRepository {

		private final Map<Long, Long> accountIds = new HashMap<>();
		private final Map<Long, Long> balances = new HashMap<>();

		@Override
		public Optional<PointAccount> findByUserId(Long userId) {
			if (!accountIds.containsKey(userId)) {
				return Optional.empty();
			}
			PointAccount account = PointAccount.open(userId, LocalDateTime.of(2026, 11, 1, 0, 0));
			setField(account, "id", accountIds.get(userId));
			setField(account, "balance", balances.get(userId));
			return Optional.of(account);
		}

		@Override
		public BalanceChange increase(Long userId, long amount, LocalDateTime nowUtc) {
			Long accountId = accountIds.computeIfAbsent(userId, id -> (long) accountIds.size() + 1);
			long balance = balances.merge(userId, amount, Long::sum);
			return new BalanceChange(accountId, balance);
		}

		@Override
		public Optional<BalanceChange> decrease(Long userId, long amount, LocalDateTime nowUtc) {
			if (!accountIds.containsKey(userId) || balances.get(userId) < amount) {
				return Optional.empty();
			}
			return Optional.of(new BalanceChange(accountIds.get(userId), balances.merge(userId, -amount, Long::sum)));
		}

		Long accountIdOf(Long userId) { return accountIds.get(userId); }
		long balanceOf(Long userId) { return balances.getOrDefault(userId, 0L); }
	}

	private static class FakeTransactionRepository implements PointTransactionRepository {

		private final List<PointTransaction> saved = new ArrayList<>();

		@Override
		public PointTransaction save(PointTransaction transaction) {
			setField(transaction, "id", (long) saved.size() + 1);
			saved.add(transaction);
			return transaction;
		}

		@Override
		public void lockIdempotencyKey(String idempotencyKey) {}

		@Override
		public Optional<PointTransaction> findByIdempotencyKey(String idempotencyKey) {
			return saved.stream().filter(t -> t.getIdempotencyKey().equals(idempotencyKey)).findFirst();
		}

		@Override
		public List<PointTransaction> findByAccountId(Long accountId, Long beforeId, int limit) {
			return saved.stream()
					.filter(t -> t.getAccountId().equals(accountId))
					.filter(t -> beforeId == null || t.getId() < beforeId)
					.sorted(Comparator.comparing(PointTransaction::getId).reversed())
					.limit(limit)
					.toList();
		}
	}

	private static void setField(Object target, String name, Object value) {
		try {
			Field field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			field.set(target, value);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
