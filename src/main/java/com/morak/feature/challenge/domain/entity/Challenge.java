package com.morak.feature.challenge.domain.entity;

import com.morak.common.entity.BaseEntity;
import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import com.morak.feature.challenge.domain.enums.ChallengeStatus;
import com.morak.feature.challenge.domain.enums.ChallengeVisibility;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "challenge")
public class Challenge extends BaseEntity {

	private static final Logger log = LoggerFactory.getLogger(Challenge.class);

	@Getter
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long ownerUserId;
	private String title;

	@Enumerated(EnumType.STRING)
	private ChallengeVisibility visibility;

	@Enumerated(EnumType.STRING)
	private ChallengeCategory category;

	private String description;
	private String purpose;
	private String commonMission;
	private String verificationRules;
	private String expectations;
	@Column(length = 300)
	private String coverImageKey;

	@JdbcTypeCode(SqlTypes.CHAR)
	private String inviteCode;

	@Enumerated(EnumType.STRING)
	private ChallengeStatus status;

	private LocalDate startDate;
	private LocalDate endDate;
	private int capacity;
	private int currentCount;
	@Column(name = "point_amount", nullable = false)
	private int pointAmount;

	protected Challenge() {}

	public Challenge(long ownerUserId, String title, ChallengeVisibility visibility, ChallengeCategory category,
			String description, String purpose, String commonMission, String verificationRules, String expectations, String inviteCode,
			LocalDate startDate, LocalDate endDate, int capacity, int pointAmount, String coverImageKey) {
		this.ownerUserId = ownerUserId;
		this.title = title;
		this.visibility = visibility;
		this.category = category;
		this.description = description;
		this.purpose = purpose;
		this.commonMission = commonMission;
		this.verificationRules = verificationRules;
		this.expectations = expectations;
		this.coverImageKey = coverImageKey;
		this.inviteCode = inviteCode;
		this.startDate = startDate;
		this.endDate = endDate;
		this.capacity = capacity;
		this.pointAmount = pointAmount;
		this.status = ChallengeStatus.RECRUITING;
		this.currentCount = 1;
	}

	public long getOwnerUserId() { return ownerUserId; }
	public String getTitle() { return title; }
	public ChallengeVisibility getVisibility() { return visibility; }
	public String getInviteCode() { return inviteCode; }
	public ChallengeStatus getStatus() { return status; }
	public boolean isDiscarded() { return status == ChallengeStatus.DISCARDED; }
	public int getCapacity() { return capacity; }
	public int getCurrentCount() { return currentCount; }
	public int getPointAmount() { return pointAmount; }
	public LocalDate getStartDate() { return startDate; }
	public LocalDate getEndDate() { return endDate; }

	/** 새 참여자를 받을 수 있는 상태인지 (위키 §4.3: RECRUITING 또는 START_HELD). */
	public boolean isRecruiting() {
		return status == ChallengeStatus.RECRUITING || status == ChallengeStatus.START_HELD;
	}

	/** 시작 보류 상태가 아니면 거절한다. 폐기 전에 잠금 없이 빠르게 확인할 때 쓴다. */
	public void requireStartHeld() {
		requireStartHeld(ChallengeStatus.DISCARDED);
	}

	private void requireStartHeld(ChallengeStatus target) {
		if (status != ChallengeStatus.START_HELD) {
			log.warn("챌린지 상태 전이 거절: challengeId={}, from={}, to={}", id, status, target);
			throw new BusinessException(ChallengeErrorCode.NOT_START_HELD);
		}
	}

	/**
	 * 시작 보류(START_HELD) 챌린지의 기간을 다시 정하고 모집 중(RECRUITING)으로 되돌린다(위키 §4.2).
	 * 날짜 규칙은 호출한 쪽이 {@code ChallengePeriod}로 확인한다. 참여 인원·정원·포인트는 건드리지 않는다.
	 * 마감(시작일+2일 23:59)은 컬럼이 아니라 시작일에서 계산하므로 따로 갱신할 값이 없다.
	 */
	public void reschedule(LocalDate newStartDate, LocalDate newEndDate) {
		requireStartHeld(ChallengeStatus.RECRUITING);
		this.startDate = newStartDate;
		this.endDate = newEndDate;
		this.status = ChallengeStatus.RECRUITING;
	}

	/**
	 * 시작 보류(START_HELD) 챌린지를 폐기한다(위키 §4.2). 시작 후에는 임의로 폐기할 수 없으므로 다른 상태는 거절한다.
	 * 참가자·포인트 처리는 호출한 쪽이 한다. 상태만 바꾸고 참여 인원(currentCount)은 기록으로 남긴다.
	 */
	public void discard() {
		requireStartHeld(ChallengeStatus.DISCARDED);
		status = ChallengeStatus.DISCARDED;
	}

	/** 방장이 바뀐다(방장·부방장 승계). 참가자의 역할 변경은 참가 도메인이 한다. */
	public void changeOwner(long newOwnerUserId) {
		this.ownerUserId = newOwnerUserId;
	}

	/** 승인으로 참여 인원이 늘어난다. 정원 확인은 호출한 쪽이 먼저 하므로, 여기서는 불변식만 지킨다. */
	public void acceptMember() {
		if (currentCount >= capacity) {
			throw new IllegalStateException("정원이 찬 챌린지에 참여자를 더할 수 없습니다: challengeId=" + id);
		}
		currentCount++;
	}
}
