package com.morak.feature.participation.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 챌린지 종료 후 참가자별 결과(집계 확정본). 정산이 확정될 때 한 번 만들고 이후 바꾸지 않는다.
 * 참가자당 한 행이며 participation_id 유니크 인덱스가 중복을 막는다.
 */
@Entity
@Table(name = "participation_result")
public class ParticipationResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private long participationId;

	/** 조회용 복사본이다 (ID만 저장). */
	@Column(nullable = false)
	private long challengeId;

	/** 조회용 복사본이다 (ID만 저장). */
	@Column(nullable = false)
	private long userId;

	@Column(nullable = false)
	private int approvedCount;

	@Column(nullable = false)
	private int totalCount;

	@Column(nullable = false)
	private BigDecimal achievementRate;

	@Column(nullable = false)
	private int currentStreak;

	@Column(nullable = false)
	private int maxStreak;

	@Column(nullable = false)
	private boolean completed;

	@Column(nullable = false)
	private LocalDateTime finalizedAt;

	protected ParticipationResult() {}

	private ParticipationResult(long participationId, long challengeId, long userId, int approvedCount, int totalCount,
			int currentStreak, int maxStreak, boolean completed, LocalDateTime finalizedAt) {
		this.participationId = participationId;
		this.challengeId = challengeId;
		this.userId = userId;
		this.approvedCount = approvedCount;
		this.totalCount = totalCount;
		this.achievementRate = AchievementRate.percent(approvedCount, totalCount);
		this.currentStreak = currentStreak;
		this.maxStreak = maxStreak;
		this.completed = completed;
		this.finalizedAt = finalizedAt;
	}

	/**
	 * 정산 확정 시점의 참가자 결과를 만든다. 인증률은 승인 수와 대상 수로 여기서 계산한다.
	 * 연속 인증 일수와 완주 여부는 계산 규칙이 위키에 없거나 호출한 쪽이 판단하는 값이라 그대로 받는다.
	 * TODO(D-28): 연속 인증 일수 계산 규칙은 PM 확인 전이다. 인증 쪽에서 계산해 넘긴다.
	 */
	public static ParticipationResult of(long participationId, long challengeId, long userId, int approvedCount,
			int totalCount, int currentStreak, int maxStreak, boolean completed, Instant finalizedAt) {
		if (approvedCount < 0 || totalCount < approvedCount) {
			throw new IllegalArgumentException(
					"승인 수는 0 이상이고 인증 대상 수를 넘을 수 없습니다: approved=" + approvedCount + ", total=" + totalCount);
		}
		if (currentStreak < 0 || maxStreak < currentStreak) {
			throw new IllegalArgumentException(
					"연속 인증 일수가 올바르지 않습니다: current=" + currentStreak + ", max=" + maxStreak);
		}
		return new ParticipationResult(participationId, challengeId, userId, approvedCount, totalCount, currentStreak,
				maxStreak, completed, LocalDateTime.ofInstant(finalizedAt, ZoneOffset.UTC));
	}

	public Long getId() {
		return id;
	}

	public long getParticipationId() {
		return participationId;
	}

	public long getChallengeId() {
		return challengeId;
	}

	public long getUserId() {
		return userId;
	}

	public int getApprovedCount() {
		return approvedCount;
	}

	public int getTotalCount() {
		return totalCount;
	}

	public BigDecimal getAchievementRate() {
		return achievementRate;
	}

	public int getCurrentStreak() {
		return currentStreak;
	}

	public int getMaxStreak() {
		return maxStreak;
	}

	public boolean isCompleted() {
		return completed;
	}

	public LocalDateTime getFinalizedAt() {
		return finalizedAt;
	}
}
