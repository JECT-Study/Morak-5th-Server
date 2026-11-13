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

	/** 지원서를 낸 승인 대기 참가자. 포인트를 거는 지원은 포인트 도메인 연동 후에 별도로 만든다. */
	public static Participation apply(long challengeId, long userId, String applyMessage, Instant now) {
		return new Participation(challengeId, userId, ParticipationRole.MEMBER, ParticipationStatus.PENDING,
				applyMessage, toUtc(now));
	}

	/** 챌린지를 개설한 방장. 지원·승인 없이 바로 참여 중(ACTIVE)이 된다. */
	public static Participation host(long challengeId, long userId, Instant now) {
		Participation participation = new Participation(challengeId, userId, ParticipationRole.HOST,
				ParticipationStatus.ACTIVE, null, toUtc(now));
		participation.joinedAt = participation.appliedAt;
		return participation;
	}

	/** 동시 참여 한도에 걸려 대기 신청이 무효가 된다. 승인 대기 상태에서만 가능하다. */
	public void invalidate() {
		if (status != ParticipationStatus.PENDING) {
			log.warn("참가 상태 전이 거절: participationId={}, from={}, to={}", id, status, ParticipationStatus.INVALIDATED);
			throw new BusinessException(ParticipationErrorCode.INVALID_STATUS_TRANSITION);
		}
		status = ParticipationStatus.INVALIDATED;
	}

	/** 포인트가 걸려 있어 무효화 때 반환 처리가 필요한지 여부. */
	public boolean hasPoints() {
		return pointStatus != ParticipationPointStatus.NONE;
	}

	public Long getId() {
		return id;
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
