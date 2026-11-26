package com.morak.feature.participation.domain.entity;

import com.morak.common.exception.BusinessException;
import com.morak.feature.participation.domain.enums.ParticipationPointStatus;
import com.morak.feature.participation.domain.enums.ParticipationRole;
import com.morak.feature.participation.domain.enums.ParticipationStatus;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 챌린지 참가자. 신청부터 퇴장·완주까지 한 행을 계속 사용한다. */
@Entity
@Table(name = "participation")
public class Participation {

	private static final Logger log = LoggerFactory.getLogger(Participation.class);

	public static final String END_REASON_DISCARDED = "DISCARDED";
	public static final String END_REASON_FAIL_LIMIT = "FAIL_LIMIT";
	public static final String END_REASON_EARLY_END = "EARLY_END";
	public static final String END_REASON_COMPLETED = "COMPLETED";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private long challengeId;

	@Column(nullable = false)
	private long userId;

	@Enumerated(EnumType.STRING)
	private ParticipationRole role;

	@Enumerated(EnumType.STRING)
	private ParticipationStatus status;

	@Enumerated(EnumType.STRING)
	private ParticipationPointStatus pointStatus;

	@Column(name = "deposit_amount", nullable = false)
	private int pointAmount;

	private String applyMessage;

	@Column(nullable = false)
	private LocalDateTime appliedAt;

	private LocalDateTime joinedAt;

	/** 방장이 무효(INVALIDATED) 신청을 확인한 시각(UTC). 확인 전에는 null이며, 확인하면 승인 대기 목록에서 사라진다. */
	@Column(name = "invalid_confirmed_at")
	private LocalDateTime invalidConfirmedAt;

	/** 참여가 끝난 시각(UTC). 시작 전 폐기·퇴장·조기 종료 때 기록한다. */
	@Column(name = "ended_at")
	private LocalDateTime endedAt;

	/** 참여가 끝난 사유(위키 §5 end_reason). 시작 전 폐기는 {@link #END_REASON_DISCARDED}. */
	@Column(name = "end_reason", length = 30)
	private String endReason;

	protected Participation() {}

	private Participation(long challengeId, long userId, ParticipationRole role, ParticipationStatus status,
			String applyMessage, LocalDateTime now) {
		this.challengeId = challengeId;
		this.userId = userId;
		this.role = role;
		this.status = status;
		this.pointStatus = ParticipationPointStatus.NONE;
		this.applyMessage = applyMessage;
		this.appliedAt = now;
	}

	/** 지원서를 낸 승인 대기 참가자. 포인트 없는 챌린지의 지원이다. */
	public static Participation apply(long challengeId, long userId, String applyMessage, Instant now) {
		return new Participation(challengeId, userId, ParticipationRole.MEMBER, ParticipationStatus.PENDING,
				applyMessage, toUtc(now));
	}

	/** 지원서를 낸 승인 대기 참가자. 포인트가 있는 챌린지면 걸어 둔 포인트(HELD)를 함께 기록한다. */
	public static Participation apply(long challengeId, long userId, String applyMessage, int pointAmount, Instant now) {
		Participation participation = apply(challengeId, userId, applyMessage, now);
		if (pointAmount > 0) {
			participation.pointAmount = pointAmount;
			participation.pointStatus = ParticipationPointStatus.HELD;
		}
		return participation;
	}

	/** 방장이 승인한다. 승인 대기 상태에서만 가능하며, 승인 시각이 합류 시각이 된다. */
	public void approve(Instant now) {
		requirePending(ParticipationStatus.ACTIVE);
		status = ParticipationStatus.ACTIVE;
		joinedAt = toUtc(now);
	}

	/** 방장이 거절한다. 승인 대기 상태에서만 가능하며, 걸린 포인트는 반환 상태로 바뀐다. */
	public void reject() {
		requirePending(ParticipationStatus.REJECTED);
		status = ParticipationStatus.REJECTED;
		if (pointStatus == ParticipationPointStatus.HELD) {
			pointStatus = ParticipationPointStatus.REFUNDED;
		}
	}

	/**
	 * 챌린지 결과(participation_result) 행을 가질 수 있는 참가자인지. 참여가 확정된 적이 있어야 한다.
	 * 시작 전에 폐기된 참가자(DISCARDED)와 승인받지 못한 신청(PENDING·REJECTED·INVALIDATED)은 결과를 만들지 않는다 (위키 §4.2).
	 */
	public boolean canHaveResult() {
		return status == ParticipationStatus.ACTIVE || status == ParticipationStatus.COMPLETED
				|| status == ParticipationStatus.EXPELLED || status == ParticipationStatus.EARLY_ENDED;
	}

	/** 포인트가 걸려 있어 거절·무효화 때 반환해야 하는지. */
	public boolean isPointHeld() {
		return pointStatus == ParticipationPointStatus.HELD;
	}

	private void requirePending(ParticipationStatus to) {
		if (status != ParticipationStatus.PENDING) {
			log.warn("참가 상태 전이 거절: participationId={}, from={}, to={}", id, status, to);
			throw new BusinessException(ParticipationErrorCode.INVALID_STATUS_TRANSITION);
		}
	}

	/** 챌린지를 개설한 방장. 지원·승인 없이 바로 참여 중(ACTIVE)이 된다. */
	public static Participation host(long challengeId, long userId, Instant now) {
		Participation participation = new Participation(challengeId, userId, ParticipationRole.HOST,
				ParticipationStatus.ACTIVE, null, toUtc(now));
		participation.joinedAt = participation.appliedAt;
		return participation;
	}

	/** 방장 등록 시 개설 포인트를 참여 행에 반영한다. */
	public static Participation host(long challengeId, long userId, Instant now, int pointAmount) {
		Participation participation = host(challengeId, userId, now);
		if (pointAmount > 0) {
			participation.pointAmount = pointAmount;
			participation.pointStatus = ParticipationPointStatus.HELD;
		}
		return participation;
	}

	/**
	 * 챌린지가 시작 전에 폐기돼 참여가 끝난다(위키 §4.3). 승인 대기(PENDING)와 참여 중(ACTIVE)만 가능하며, 걸린 포인트는
	 * 반환 상태로 바뀐다. 반환(원장) 처리는 호출한 쪽이 한다.
	 * TODO(D-26): end_reason=DISCARDED와 ended_at 기록은 위키에 없는 제안이다.
	 */
	public void discard(Instant now) {
		if (status != ParticipationStatus.PENDING && status != ParticipationStatus.ACTIVE) {
			log.warn("참가 상태 전이 거절: participationId={}, from={}, to={}", id, status, ParticipationStatus.DISCARDED);
			throw new BusinessException(ParticipationErrorCode.INVALID_STATUS_TRANSITION);
		}
		status = ParticipationStatus.DISCARDED;
		if (pointStatus == ParticipationPointStatus.HELD) {
			pointStatus = ParticipationPointStatus.REFUNDED;
		}
		endedAt = toUtc(now);
		endReason = END_REASON_DISCARDED;
	}

	/** 개인 실패 누적으로 퇴장한다(위키 §4.4 퇴장). 참여 중(ACTIVE)에서만 가능하다. 포인트 차감은 정산이 한다. */
	public void expel(Instant now) {
		end(ParticipationStatus.EXPELLED, END_REASON_FAIL_LIMIT, now);
	}

	/** 공개 챌린지가 2명만 남아 조기 종료된다(위키 §4.4 조기 종료). 참여 중(ACTIVE)에서만 가능하다. */
	public void endEarly(Instant now) {
		end(ParticipationStatus.EARLY_ENDED, END_REASON_EARLY_END, now);
	}

	/** 정상 종료한 챌린지의 결과가 확정돼 완주한다(위키 §4.5). 참여 중(ACTIVE)에서만 가능하다. */
	public void complete(Instant now) {
		end(ParticipationStatus.COMPLETED, END_REASON_COMPLETED, now);
	}

	private void end(ParticipationStatus to, String reason, Instant now) {
		if (status != ParticipationStatus.ACTIVE) {
			log.warn("참가 상태 전이 거절: participationId={}, from={}, to={}", id, status, to);
			throw new BusinessException(ParticipationErrorCode.INVALID_STATUS_TRANSITION);
		}
		status = to;
		endedAt = toUtc(now);
		endReason = reason;
	}

	/** 동시 참여 한도에 걸려 대기 신청이 무효가 된다. 승인 대기 상태에서만 가능하다. */
	public void invalidate() {
		requirePending(ParticipationStatus.INVALIDATED);
		status = ParticipationStatus.INVALIDATED;
	}

	/** 포인트가 걸려 있어 무효화 때 반환 처리가 필요한지 여부. */
	public boolean hasPoints() {
		return pointStatus != ParticipationPointStatus.NONE;
	}

	/**
	 * 무효가 된 신청을 방장이 확인한다. 확인하면 승인 대기 목록에서 사라진다.
	 * 이미 확인한 신청이면 확인 시각을 바꾸지 않고 그대로 둔다 (같은 요청을 여러 번 보내도 결과가 같다).
	 */
	public void confirmInvalidation(Instant now) {
		if (status != ParticipationStatus.INVALIDATED) {
			log.warn("무효 확인 거절: participationId={}, status={}", id, status);
			throw new BusinessException(ParticipationErrorCode.INVALID_STATUS_TRANSITION);
		}
		if (invalidConfirmedAt == null) {
			invalidConfirmedAt = toUtc(now);
		}
	}

	/** 개설 후 처음 합류한 참여자를 부방장으로 지정한다. 참여 중(ACTIVE)인 일반 참가자만 가능하다. */
	public void assignDeputy() {
		requireActiveRole(ParticipationRole.MEMBER, ParticipationRole.DEPUTY);
		role = ParticipationRole.DEPUTY;
	}

	/** 방장이 떠나 부방장이 방장을 이어받는다. 참여 중(ACTIVE)인 부방장만 가능하다. */
	public void succeedAsHost() {
		requireActiveRole(ParticipationRole.DEPUTY, ParticipationRole.HOST);
		role = ParticipationRole.HOST;
	}

	/** 방장·부방장이 떠나면서 일반 참가자로 내려간다. 일반 참가자는 내릴 역할이 없어 예외다. */
	public void stepDown() {
		if (role == ParticipationRole.MEMBER) {
			log.warn("역할 전이 거절: participationId={}, from={}, to={}", id, role, ParticipationRole.MEMBER);
			throw new BusinessException(ParticipationErrorCode.INVALID_STATUS_TRANSITION);
		}
		role = ParticipationRole.MEMBER;
	}

	private void requireActiveRole(ParticipationRole expected, ParticipationRole to) {
		if (status != ParticipationStatus.ACTIVE || role != expected) {
			log.warn("역할 전이 거절: participationId={}, status={}, from={}, to={}", id, status, role, to);
			throw new BusinessException(ParticipationErrorCode.INVALID_STATUS_TRANSITION);
		}
	}

	public Long getId() {
		return id;
	}

	public int getPointAmount() {
		return pointAmount;
	}

	public long getChallengeId() {
		return challengeId;
	}

	public long getUserId() {
		return userId;
	}

	public ParticipationRole getRole() {
		return role;
	}

	public ParticipationStatus getStatus() {
		return status;
	}

	public LocalDateTime getEndedAt() {
		return endedAt;
	}

	public String getEndReason() {
		return endReason;
	}

	public LocalDateTime getJoinedAt() {
		return joinedAt;
	}

	public LocalDateTime getInvalidConfirmedAt() {
		return invalidConfirmedAt;
	}

	private static LocalDateTime toUtc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}
}
