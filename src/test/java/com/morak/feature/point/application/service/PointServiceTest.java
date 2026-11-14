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
import org.junit.jupiter.api.function.Executable;
import org.springframework.dao.DataIntegrityViolationException;

class PointServiceTest {

	private static final Long USER = 10L;
	private static final Long OTHER_USER = 20L;
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
		service.deposit(USER, 7L, TITLE, 3_000);

		PointChangeResult result = service.refund(USER, 7L, TITLE, 3_000);

		assertEquals(3_000, result.amount());
		assertEquals(10_000, result.balanceAfter());
		PointTransaction saved = transactions.saved.get(2);
		assertEquals(PointTransactionType.REFUND, saved.getType());
		assertEquals(7L, saved.getParticipationId());
		assertEquals("REFUND:7", saved.getIdempotencyKey());
		assertEquals(TITLE, saved.getChallengeTitle());
	}

	@Test
	void sameIdempotencyKeyIsAppliedOnlyOnce() {
		service.grant(USER, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:10");
		service.deposit(USER, 7L, TITLE, 3_000);

		PointChangeResult first = service.refund(USER, 7L, TITLE, 3_000);
		PointChangeResult second = service.refund(USER, 7L, TITLE, 3_000);

		assertEquals(first, second);
		assertEquals(10_000, accounts.balanceOf(USER));
		assertEquals(3, transactions.saved.size());
	}

	@Test
	void sameKeyWithDifferentAmountIsConflict() {
		service.grant(USER, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:10");
		service.deposit(USER, 7L, TITLE, 3_000);

		assertRejected(PointErrorCode.IDEMPOTENCY_KEY_CONFLICT, () -> service.deposit(USER, 7L, TITLE, 5_000));
		assertEquals(7_000, accounts.balanceOf(USER));
	}

	@Test
	void sameKeyFromAnotherUserIsConflict() {
		service.grant(USER, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:10");
		service.grant(OTHER_USER, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:20");
		service.deposit(USER, 7L, TITLE, 3_000);

		assertRejected(PointErrorCode.IDEMPOTENCY_KEY_CONFLICT, () -> service.deposit(OTHER_USER, 7L, TITLE, 3_000));
		assertEquals(10_000, accounts.balanceOf(OTHER_USER));
	}

	@Test
	void duplicateKeyAtSaveIsTranslatedToConflict() {
		transactions.failNextSave = true;

		assertRejected(PointErrorCode.IDEMPOTENCY_KEY_CONFLICT,
				() -> service.grant(USER, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:10"));
	}

	@Test
	void refundWithoutDepositIsRejected() {
		service.grant(USER, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:10");

		assertRejected(PointErrorCode.DEPOSIT_NOT_FOUND, () -> service.refund(USER, 7L, TITLE, 3_000));
		assertEquals(10_000, accounts.balanceOf(USER));
	}

	@Test
	void refundOfAnotherUsersDepositIsRejected() {
		service.grant(OTHER_USER, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:20");
		service.deposit(OTHER_USER, 7L, TITLE, 3_000);

		assertRejected(PointErrorCode.DEPOSIT_NOT_FOUND, () -> service.refund(USER, 7L, TITLE, 3_000));
		assertEquals(0, accounts.balanceOf(USER));
	}

	@Test
	void refundOverDepositIsRejectedButPartialRefundIsAllowed() {
		service.grant(USER, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:10");
		service.deposit(USER, 7L, TITLE, 3_000);

		assertRejected(PointErrorCode.REFUND_EXCEEDS_DEPOSIT, () -> service.refund(USER, 7L, TITLE, 3_001));
		assertEquals(9_000, service.refund(USER, 7L, TITLE, 2_000).balanceAfter());
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
	void missingRequiredValuesAreRejected() {
		assertRejected(PointErrorCode.MISSING_REQUIRED_VALUE,
				() -> service.grant(null, PointGrantReason.SIGNUP_GIFT, 1_000, "k"));
		assertRejected(PointErrorCode.MISSING_REQUIRED_VALUE, () -> service.grant(USER, null, 1_000, "k"));
		assertRejected(PointErrorCode.MISSING_REQUIRED_VALUE, () -> service.deposit(USER, null, TITLE, 1_000));
		assertRejected(PointErrorCode.MISSING_REQUIRED_VALUE, () -> service.refund(null, 7L, TITLE, 1_000));
	}

	@Test
	void invalidIdempotencyKeyIsRejected() {
		assertRejected(PointErrorCode.INVALID_IDEMPOTENCY_KEY,
				() -> service.grant(USER, PointGrantReason.SIGNUP_GIFT, 1_000, null));
		assertRejected(PointErrorCode.INVALID_IDEMPOTENCY_KEY,
				() -> service.grant(USER, PointGrantReason.SIGNUP_GIFT, 1_000, " "));
		assertRejected(PointErrorCode.INVALID_IDEMPOTENCY_KEY,
				() -> service.grant(USER, PointGrantReason.SIGNUP_GIFT, 1_000, "k".repeat(101)));
		service.grant(USER, PointGrantReason.SIGNUP_GIFT, 1_000, "k".repeat(100));
	}

	@Test
	void invalidChallengeTitleIsRejected() {
		service.grant(USER, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:10");

		assertRejected(PointErrorCode.INVALID_CHALLENGE_TITLE, () -> service.deposit(USER, 7L, null, 1_000));
		assertRejected(PointErrorCode.INVALID_CHALLENGE_TITLE, () -> service.deposit(USER, 7L, "", 1_000));
		assertRejected(PointErrorCode.INVALID_CHALLENGE_TITLE, () -> service.refund(USER, 7L, "가".repeat(51), 1_000));
		service.deposit(USER, 7L, "가".repeat(50), 1_000);
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

	@Test
	void ledgerLimitIsCappedAtMaxLimit() {
		service.grant(USER, PointGrantReason.SIGNUP_GIFT, 10_000, "SIGNUP_GIFT:10");
		for (long participation = 1; participation <= 100; participation++) {
			service.deposit(USER, participation, TITLE, 1);
		}

		PointLedgerResult ledger = service.getLedger(USER, null, 1_000);

		assertEquals(PointService.MAX_LIMIT, ledger.transactions().size());
		assertEquals(2L, ledger.nextCursor());
	}

	@Test
	void nonPositiveCursorIsRejected() {
		assertRejected(PointErrorCode.INVALID_CURSOR, () -> service.getLedger(USER, 0L, null));
		assertRejected(PointErrorCode.INVALID_CURSOR, () -> service.getLedger(USER, -1L, null));
	}

	private static void assertRejected(PointErrorCode expected, Executable action) {
		assertEquals(expected, assertThrows(BusinessException.class, action).getErrorCode());
	}

	private static List<Long> ids(PointLedgerResult ledger) {
		return ledger.transactions().stream().map(PointLedgerResult.Entry::id).toList();
	}

	/** 실제 DB 락은 없다. 동시성은 통합 테스트에서 검증한다. */
	private static class FakeAccountRepository implements PointAccountRepository {

		private final Map<Long, Long> accountIds = new HashMap<>();
		private final Map<Long, Long> balances = new HashMap<>();

		@Override
		public Optional<PointAccount> findByUserId(Long userId) {
			if (!accountIds.containsKey(userId)) {
				return Optional.empty();
			}
			Long accountId = accountIds.get(userId);
			PointAccount account = PointAccount.open(userId, LocalDateTime.of(2026, 11, 1, 0, 0));
			setField(account, "id", accountId);
			setField(account, "balance", balances.get(accountId));
			return Optional.of(account);
		}

		@Override
		public Long lockForUpdate(Long userId, LocalDateTime nowUtc) {
			Long accountId = accountIds.computeIfAbsent(userId, id -> (long) accountIds.size() + 1);
			balances.putIfAbsent(accountId, 0L);
			return accountId;
		}

		@Override
		public long increase(Long accountId, long amount, LocalDateTime nowUtc) {
			return balances.merge(accountId, amount, Long::sum);
		}

		@Override
		public Optional<Long> decrease(Long accountId, long amount, LocalDateTime nowUtc) {
			if (balances.get(accountId) < amount) {
				return Optional.empty();
			}
			return Optional.of(balances.merge(accountId, -amount, Long::sum));
		}

		Long accountIdOf(Long userId) { return accountIds.get(userId); }
		long balanceOf(Long userId) { return balances.getOrDefault(accountIds.get(userId), 0L); }
	}

	private static class FakeTransactionRepository implements PointTransactionRepository {

		private final List<PointTransaction> saved = new ArrayList<>();
		/** 다른 유저가 같은 키로 동시에 저장해 유니크 제약에 걸린 상황을 흉내 낸다. */
		private boolean failNextSave;

		@Override
		public PointTransaction save(PointTransaction transaction) {
			if (failNextSave) {
				failNextSave = false;
				throw new DataIntegrityViolationException("uk_point_transaction_idempotency");
			}
			setField(transaction, "id", (long) saved.size() + 1);
			saved.add(transaction);
			return transaction;
		}

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
