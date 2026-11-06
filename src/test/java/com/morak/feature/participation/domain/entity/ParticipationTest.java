package com.morak.feature.participation.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import com.morak.feature.participation.domain.vo.DepositStatus;
import com.morak.feature.participation.domain.vo.ParticipationEndReason;
import com.morak.feature.participation.domain.vo.ParticipationRole;
import com.morak.feature.participation.domain.vo.ParticipationStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ParticipationTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 10, 0);
	private static final LocalDateTime LATER = NOW.plusDays(1);
	private static final long HOST_USER = 1L;

	private Participation applied() {
		return Participation.apply(10L, 2L, "열심히 하겠습니다", NOW);
	}

	private Participation active() {
		Participation participation = applied();
		participation.approve(HOST_USER, 0, null, NOW);
		return participation;
	}

	private void assertError(ParticipationErrorCode expected, Runnable action) {
		assertEquals(expected, assertThrows(BusinessException.class, action::run).getErrorCode());
	}

	@Test
	void applyStartsAsPendingMember() {
		Participation participation = applied();

		assertEquals(ParticipationStatus.PENDING, participation.getStatus());
		assertEquals(ParticipationRole.MEMBER, participation.getRole());
		assertEquals(DepositStatus.NONE, participation.getDepositStatus());
		assertEquals(0, participation.getDepositAmount());
		assertEquals(NOW, participation.getAppliedAt());
		assertNull(participation.getJoinedAt());
	}

	@Test
	void applyRejectsTooLongMessage() {
		BusinessException exception = assertThrows(BusinessException.class,
				() -> Participation.apply(10L, 2L, "가".repeat(Participation.MAX_MESSAGE_LENGTH + 1), NOW));

		assertEquals(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
	}

	@Test
	void hostJoinsImmediately() {
		Participation host = Participation.host(10L, 1L, NOW);

		assertEquals(ParticipationRole.HOST, host.getRole());
		assertEquals(ParticipationStatus.ACTIVE, host.getStatus());
		assertEquals(NOW, host.getJoinedAt());
	}

	@Test
	void approveWithoutDepositActivatesImmediately() {
		Participation participation = applied();

		participation.approve(HOST_USER, 0, null, LATER);

		assertEquals(ParticipationStatus.ACTIVE, participation.getStatus());
		assertEquals(HOST_USER, participation.getDecidedBy());
		assertEquals(LATER, participation.getDecidedAt());
		assertEquals(LATER, participation.getJoinedAt());
	}

	@Test
	void approveWithDepositWaitsForPayment() {
		Participation participation = applied();
		LocalDateTime due = LATER.plusHours(24);

		participation.approve(HOST_USER, 5000, due, LATER);

		assertEquals(ParticipationStatus.APPROVED, participation.getStatus());
		assertEquals(5000, participation.getDepositAmount());
		assertEquals(DepositStatus.NONE, participation.getDepositStatus());
		assertEquals(due, participation.getPaymentDueAt());
		assertNull(participation.getJoinedAt());
	}

	@Test
	void approveWithDepositRequiresPaymentDue() {
		Participation participation = applied();

		BusinessException exception = assertThrows(BusinessException.class,
				() -> participation.approve(HOST_USER, 5000, null, LATER));

		assertEquals(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
		assertEquals(ParticipationStatus.PENDING, participation.getStatus());
	}

	@Test
	void confirmPaymentActivatesAndMarksDepositPaid() {
		Participation participation = applied();
		participation.approve(HOST_USER, 5000, LATER.plusHours(24), LATER);

		participation.confirmPayment(LATER.plusHours(1));

		assertEquals(ParticipationStatus.ACTIVE, participation.getStatus());
		assertEquals(DepositStatus.PAID, participation.getDepositStatus());
		assertEquals(LATER.plusHours(1), participation.getJoinedAt());
	}

	@Test
	void expiredPaymentCancelsWithReason() {
		Participation participation = applied();
		participation.approve(HOST_USER, 5000, LATER, NOW);

		participation.expirePayment(LATER);

		assertEquals(ParticipationStatus.CANCELED, participation.getStatus());
		assertEquals(ParticipationEndReason.PAYMENT_EXPIRED, participation.getEndReason());
		assertEquals(LATER, participation.getEndedAt());
	}

	@Test
	void rejectRecordsDecision() {
		Participation participation = applied();

		participation.reject(HOST_USER, "NOT_FIT", "목표가 달라요", LATER);

		assertEquals(ParticipationStatus.REJECTED, participation.getStatus());
		assertEquals("NOT_FIT", participation.getRejectReasonType());
		assertEquals("목표가 달라요", participation.getRejectReason());
		assertEquals(HOST_USER, participation.getDecidedBy());
	}

	@Test
	void canceledOrRejectedApplicationCanBeReappliedOnTheSameRow() {
		Participation canceled = applied();
		canceled.cancel();
		Participation rejected = applied();
		rejected.reject(HOST_USER, "NOT_FIT", "사유", NOW);

		for (Participation participation : new Participation[] {canceled, rejected}) {
			participation.reapply("다시 신청합니다", LATER);

			assertEquals(ParticipationStatus.PENDING, participation.getStatus());
			assertEquals("다시 신청합니다", participation.getApplyMessage());
			assertEquals(LATER, participation.getAppliedAt());
			assertNull(participation.getRejectReason());
			assertNull(participation.getDecidedBy());
			assertNull(participation.getDecidedAt());
		}
	}

	@Test
	void reapplyIsRejectedUnlessCanceledOrRejected() {
		assertError(ParticipationErrorCode.ALREADY_APPLIED, () -> applied().reapply("x", LATER));
		assertError(ParticipationErrorCode.ALREADY_APPLIED, () -> active().reapply("x", LATER));
	}

	@Test
	void cancelWorksBeforeAndAfterApprovalButNotAfterJoining() {
		Participation pending = applied();
		Participation waitingPayment = applied();
		waitingPayment.approve(HOST_USER, 5000, LATER, NOW);

		pending.cancel();
		waitingPayment.cancel();

		assertEquals(ParticipationStatus.CANCELED, pending.getStatus());
		assertEquals(ParticipationStatus.CANCELED, waitingPayment.getStatus());
		assertError(ParticipationErrorCode.INVALID_STATUS_TRANSITION, () -> active().cancel());
	}

	@Test
	void leaveEndsParticipationWithSelfLeave() {
		Participation participation = active();

		participation.leave(LATER);

		assertEquals(ParticipationStatus.LEFT, participation.getStatus());
		assertEquals(ParticipationEndReason.SELF_LEAVE, participation.getEndReason());
		assertEquals(LATER, participation.getEndedAt());
	}

	@Test
	void expulsionGoesThroughNoticeAndKeepsReason() {
		Participation participation = active();

		participation.noticeExpulsion();
		assertEquals(ParticipationStatus.EXPEL_NOTICED, participation.getStatus());
		participation.expel(ParticipationEndReason.MISSED_LIMIT, LATER);

		assertEquals(ParticipationStatus.EXPELLED, participation.getStatus());
		assertEquals(ParticipationEndReason.MISSED_LIMIT, participation.getEndReason());
		assertEquals(LATER, participation.getEndedAt());
	}

	@Test
	void hostCanExpelActiveParticipantDirectly() {
		Participation participation = active();

		participation.expel(ParticipationEndReason.HOST_EXPEL, LATER);

		assertEquals(ParticipationStatus.EXPELLED, participation.getStatus());
		assertEquals(ParticipationEndReason.HOST_EXPEL, participation.getEndReason());
	}

	@Test
	void expelRejectsReasonsThatAreNotExpulsions() {
		Participation participation = active();

		BusinessException exception = assertThrows(BusinessException.class,
				() -> participation.expel(ParticipationEndReason.SELF_LEAVE, LATER));

		assertEquals(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
		assertEquals(ParticipationStatus.ACTIVE, participation.getStatus());
	}

	@Test
	void completeEndsActiveParticipation() {
		Participation participation = active();

		participation.complete(LATER);

		assertEquals(ParticipationStatus.COMPLETED, participation.getStatus());
		assertEquals(ParticipationEndReason.COMPLETED, participation.getEndReason());
		assertEquals(LATER, participation.getEndedAt());
	}

	@Test
	void invalidTransitionsAreRejected() {
		assertError(ParticipationErrorCode.INVALID_STATUS_TRANSITION, () -> applied().confirmPayment(LATER));
		assertError(ParticipationErrorCode.INVALID_STATUS_TRANSITION, () -> applied().leave(LATER));
		assertError(ParticipationErrorCode.INVALID_STATUS_TRANSITION, () -> applied().complete(LATER));
		assertError(ParticipationErrorCode.INVALID_STATUS_TRANSITION, () -> active().approve(HOST_USER, 0, null, LATER));
		assertError(ParticipationErrorCode.INVALID_STATUS_TRANSITION, () -> active().reject(HOST_USER, "T", "r", LATER));
		assertError(ParticipationErrorCode.INVALID_STATUS_TRANSITION, () -> active().expirePayment(LATER));

		Participation ended = active();
		ended.leave(LATER);
		assertError(ParticipationErrorCode.INVALID_STATUS_TRANSITION, () -> ended.noticeExpulsion());
		assertError(ParticipationErrorCode.INVALID_STATUS_TRANSITION,
				() -> ended.expel(ParticipationEndReason.HOST_EXPEL, LATER));
		assertError(ParticipationErrorCode.INVALID_STATUS_TRANSITION, () -> ended.complete(LATER));
	}
}
