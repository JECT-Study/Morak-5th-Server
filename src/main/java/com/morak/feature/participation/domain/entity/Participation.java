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

	/** 동시 참여 한도에 걸려 대기 신청이 무효가 된다. 승인 대기 상태에서만 가능하다. */
	public void invalidate() {
		requirePending(ParticipationStatus.INVALIDATED);
		status = ParticipationStatus.INVALIDATED;
	}

	/** 포인트가 걸려 있어 무효화 때 반환 처리가 필요한지 여부. */
	public boolean hasPoints() {
		return pointStatus != ParticipationPointStatus.NONE;
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

	private static LocalDateTime toUtc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}
}
