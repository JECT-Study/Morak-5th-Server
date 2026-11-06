package com.morak.feature.participation.domain.entity;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import com.morak.feature.participation.domain.vo.DepositStatus;
import com.morak.feature.participation.domain.vo.ParticipationEndReason;
import com.morak.feature.participation.domain.vo.ParticipationRole;
import com.morak.feature.participation.domain.vo.ParticipationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;

/** 챌린지 참가자. 신청부터 퇴장·완주까지 한 행을 계속 사용한다. */
@Entity
@Table(name = "participation")
@Getter
public class Participation {

	public static final int MAX_MESSAGE_LENGTH = 200;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "challenge_id", nullable = false)
	private Long challengeId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ParticipationRole role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ParticipationStatus status;

	@Column(name = "apply_message", length = MAX_MESSAGE_LENGTH)
	private String applyMessage;

	@Column(name = "reject_reason_type", length = 30)
	private String rejectReasonType;

	@Column(name = "reject_reason", length = MAX_MESSAGE_LENGTH)
	private String rejectReason;

	@Column(name = "decided_by")
	private Long decidedBy;

	@Column(name = "decided_at")
	private LocalDateTime decidedAt;

	@Column(name = "deposit_amount", nullable = false)
	private int depositAmount;

	@Enumerated(EnumType.STRING)
	@Column(name = "deposit_status", nullable = false, length = 20)
	private DepositStatus depositStatus;

	@Column(name = "payment_due_at")
	private LocalDateTime paymentDueAt;

	@Column(name = "missed_count", nullable = false)
	private int missedCount;

	@Column(name = "last_evaluated_date")
	private LocalDate lastEvaluatedDate;

	@Column(name = "applied_at", nullable = false)
	private LocalDateTime appliedAt;

	@Column(name = "joined_at")
	private LocalDateTime joinedAt;

	@Column(name = "ended_at")
	private LocalDateTime endedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "end_reason", length = 30)
	private ParticipationEndReason endReason;

	protected Participation() {}

	private Participation(Long challengeId, Long userId, ParticipationRole role, ParticipationStatus status,
			String applyMessage, LocalDateTime appliedAt) {
		this.challengeId = challengeId;
		this.userId = userId;
		this.role = role;
		this.status = status;
		this.applyMessage = applyMessage;
		this.depositStatus = DepositStatus.NONE;
		this.appliedAt = appliedAt;
	}

	/** 일반 참가 신청. 보증금은 승인 시점에 챌린지 값을 복사한다. */
	public static Participation apply(Long challengeId, Long userId, String applyMessage, LocalDateTime now) {
		requireValidText(applyMessage);
		return new Participation(challengeId, userId, ParticipationRole.MEMBER, ParticipationStatus.PENDING,
				applyMessage, now);
	}

	/** 챌린지를 만든 방장. 신청·승인 없이 바로 참여 중 상태가 된다. */
	public static Participation host(Long challengeId, Long userId, LocalDateTime now) {
		Participation participation = new Participation(challengeId, userId, ParticipationRole.HOST,
				ParticipationStatus.ACTIVE, null, now);
		participation.joinedAt = now;
		return participation;
	}

	/** 취소·거절된 신청을 같은 행으로 다시 신청한다. */
	public void reapply(String applyMessage, LocalDateTime now) {
		if (status != ParticipationStatus.CANCELED && status != ParticipationStatus.REJECTED) {
			throw new BusinessException(ParticipationErrorCode.ALREADY_APPLIED);
		}
		requireValidText(applyMessage);
		status = ParticipationStatus.PENDING;
		this.applyMessage = applyMessage;
		appliedAt = now;
		rejectReasonType = null;
		rejectReason = null;
		decidedBy = null;
		decidedAt = null;
		depositAmount = 0;
		depositStatus = DepositStatus.NONE;
		paymentDueAt = null;
		joinedAt = null;
		endedAt = null;
		endReason = null;
	}

	/** 신청자가 취소한다. 승인 후 납부 전에도 취소할 수 있다. */
	public void cancel() {
		requireStatus(ParticipationStatus.PENDING, ParticipationStatus.APPROVED);
		status = ParticipationStatus.CANCELED;
	}

	/**
	 * 신청을 승인한다. 보증금이 있으면 납부 대기(APPROVED)가 되고, 없으면 바로 참여 중(ACTIVE)이 된다.
	 *
	 * @param challengeDepositAmount 승인 시점의 챌린지 보증금(원)
	 * @param paymentDueAt 납부 기한. 보증금이 있을 때만 필요하다.
	 */
	public void approve(Long decidedBy, int challengeDepositAmount, LocalDateTime paymentDueAt, LocalDateTime now) {
		requireStatus(ParticipationStatus.PENDING);
		if (challengeDepositAmount < 0 || (challengeDepositAmount > 0 && paymentDueAt == null)) {
			throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
		}
		this.decidedBy = decidedBy;
		decidedAt = now;
		if (challengeDepositAmount == 0) {
			status = ParticipationStatus.ACTIVE;
			joinedAt = now;
			return;
		}
		status = ParticipationStatus.APPROVED;
		depositAmount = challengeDepositAmount;
		this.paymentDueAt = paymentDueAt;
	}

	public void reject(Long decidedBy, String reasonType, String reason, LocalDateTime now) {
		requireStatus(ParticipationStatus.PENDING);
		requireValidText(reason);
		status = ParticipationStatus.REJECTED;
		this.decidedBy = decidedBy;
		decidedAt = now;
		rejectReasonType = reasonType;
		rejectReason = reason;
	}

	/** 보증금 납부가 끝나 참여가 확정된다. */
	public void confirmPayment(LocalDateTime now) {
		requireStatus(ParticipationStatus.APPROVED);
		status = ParticipationStatus.ACTIVE;
		depositStatus = DepositStatus.PAID;
		joinedAt = now;
	}

	/** 납부 기한이 지나 자동 취소된다. */
	public void expirePayment(LocalDateTime now) {
		requireStatus(ParticipationStatus.APPROVED);
		status = ParticipationStatus.CANCELED;
		end(ParticipationEndReason.PAYMENT_EXPIRED, now);
	}

	public void leave(LocalDateTime now) {
		requireStatus(ParticipationStatus.ACTIVE, ParticipationStatus.EXPEL_NOTICED);
		status = ParticipationStatus.LEFT;
		end(ParticipationEndReason.SELF_LEAVE, now);
	}

	/** 퇴장 예정 공지를 받은 상태. 이후 퇴장 처리된다. */
	public void noticeExpulsion() {
		requireStatus(ParticipationStatus.ACTIVE);
		status = ParticipationStatus.EXPEL_NOTICED;
	}

	public void expel(ParticipationEndReason reason, LocalDateTime now) {
		requireStatus(ParticipationStatus.ACTIVE, ParticipationStatus.EXPEL_NOTICED);
		if (reason != ParticipationEndReason.MISSED_LIMIT && reason != ParticipationEndReason.HOST_EXPEL) {
			throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
		}
		status = ParticipationStatus.EXPELLED;
		end(reason, now);
	}

	public void complete(LocalDateTime now) {
		requireStatus(ParticipationStatus.ACTIVE);
		status = ParticipationStatus.COMPLETED;
		end(ParticipationEndReason.COMPLETED, now);
	}

	private void end(ParticipationEndReason reason, LocalDateTime now) {
		endReason = reason;
		endedAt = now;
	}

	private void requireStatus(ParticipationStatus... allowed) {
		for (ParticipationStatus candidate : allowed) {
			if (status == candidate) {
				return;
			}
		}
		throw new BusinessException(ParticipationErrorCode.INVALID_STATUS_TRANSITION);
	}

	private static void requireValidText(String text) {
		if (text != null && text.length() > MAX_MESSAGE_LENGTH) {
			throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
		}
	}
}
