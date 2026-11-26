package com.morak.feature.challenge.domain.entity;

import com.morak.common.entity.BaseEntity;
import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import com.morak.feature.challenge.domain.enums.ChallengeStatus;
import com.morak.feature.challenge.domain.enums.ChallengeVisibility;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import com.morak.feature.challenge.domain.policy.ChallengePeriod;
import com.morak.feature.challenge.domain.policy.RecruitDeadline;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
	/** 시작 보류로 바뀐 KST 날짜. 누락 보정으로 예정 시작일보다 늦게 보류될 수 있어 시작일과 따로 둔다(D-29). */
	private LocalDate startHeldOn;
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
	 * 상태를 먼저 확인한 뒤 새 시작일이 KST 기준 내일 이후인지 확인한다. 최소 7일 규칙은 요청 명령을 만들 때 이미 확인한다.
	 * 참여 인원·정원·포인트는 건드리지 않는다. 마감(시작일+2일 23:59)은 컬럼이 아니라 시작일에서 계산하므로 따로 갱신할 값이 없다.
	 */
	public void reschedule(LocalDate newStartDate, LocalDate newEndDate, Instant now) {
		requireStartHeld(ChallengeStatus.RECRUITING);
		ChallengePeriod.validateStartDate(newStartDate, now);
		this.startDate = newStartDate;
		this.endDate = newEndDate;
		this.startHeldOn = null;
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

	/** 최소 인원(공개 3 / 비공개 2, 방장 포함)을 채웠는지. 승인 대기는 세지 않은 합류 인원을 받는다. */
	public boolean hasEnoughMembers(int joinedCount) {
		return joinedCount >= visibility.minParticipants();
	}

	/** 추가 모집 마감 날짜(예정 시작일을 1일째로 센 3일째, 시작일 + 2일). 그 날 23:59까지다. */
	public LocalDate recruitDeadlineDate() {
		return RecruitDeadline.dateOf(startDate);
	}

	/** 추가 모집이 끝나는 시각. 마감일 다음 날 00:00 KST이며, 이 시각 전에 합류한 인원만 연장 중 충족으로 본다. */
	public Instant recruitDeadlineEnd() {
		return RecruitDeadline.endOf(startDate);
	}

	/**
	 * 자동 폐기 스케줄러가 폐기할 수 있는지(위키 §4.9 ③). 시작 보류이고 추가 모집 마감일(예정 시작일 + 2일)이 오늘보다 앞설 때,
	 * 즉 마감일 23:59가 지난 다음 날 00:00부터다. 서버가 멈춰 며칠 지났어도 같다(누락 보정).
	 */
	public boolean isDiscardDue(LocalDate today) {
		return status == ChallengeStatus.START_HELD && recruitDeadlineDate().isBefore(today);
	}

	/**
	 * 시작 처리 스케줄러가 평가할 수 있는 상태인지(위키 §4.9 ②).
	 * 모집 중은 시작일이 오늘이거나 지났을 때(누락 보정 포함), 시작 보류는 실제로 보류된 날의 다음 날부터다.
	 * 보류된 당일에는 시작하지 않는다("연장 중 충족하면 다음 날 00:00 자동 시작"). 누락 보정으로 시작일보다 늦게 보류됐어도
	 * 보류한 날을 기준으로 한다.
	 */
	public boolean isStartDue(LocalDate today) {
		return switch (status) {
			case RECRUITING -> !startDate.isAfter(today);
			case START_HELD -> heldOn().isBefore(today);
			default -> false;
		};
	}

	/** 보류한 날. 이 컬럼이 생기기 전에 보류된 챌린지는 값이 없으므로 예정 시작일에 보류된 것으로 본다. */
	private LocalDate heldOn() {
		return startHeldOn != null ? startHeldOn : startDate;
	}

	public LocalDate getStartHeldOn() { return startHeldOn; }

	/**
	 * 챌린지를 시작한다(위키 §4.2). 시작일은 시작되는 날로 바뀌고, 기간 길이는 유지한다(D-10):
	 * {@code end_date = today + (원래 일수 - 1)}. 예정일 당일 시작은 날짜가 그대로고, 연장 중 시작·누락 보정은 밀린다.
	 * 참여 인원·참가자 상태는 건드리지 않는다. TODO(D-29): 누락 보정에도 같은 식을 쓰는 것은 제안이다.
	 */
	public void start(LocalDate today) {
		if (!isStartDue(today)) {
			log.warn("챌린지 시작 거절: challengeId={}, status={}, startDate={}, today={}", id, status, startDate, today);
			throw new BusinessException(ChallengeErrorCode.NOT_STARTABLE);
		}
		long daysAfterStart = ChronoUnit.DAYS.between(startDate, endDate);
		this.startDate = today;
		this.endDate = today.plusDays(daysAfterStart);
		this.status = ChallengeStatus.IN_PROGRESS;
	}

	/**
	 * 최소 인원 미달로 시작을 보류한다(위키 §4.2). 모집 중에서만 가능하고 참여 인원·정원·포인트는 그대로다.
	 * 보류한 날(today)을 기록한다. 추가 모집 마감은 여전히 예정 시작일 기준이다. TODO(D-29)
	 */
	public void holdStart(LocalDate today) {
		if (status != ChallengeStatus.RECRUITING) {
			log.warn("챌린지 상태 전이 거절: challengeId={}, from={}, to={}", id, status, ChallengeStatus.START_HELD);
			throw new BusinessException(ChallengeErrorCode.NOT_STARTABLE);
		}
		this.status = ChallengeStatus.START_HELD;
		this.startHeldOn = today;
	}

	/**
	 * 공개 챌린지가 퇴장으로 2명만 남아 오늘 조기 종료한다(위키 §4.4 조기 종료). 종료일을 오늘로 바꾸고 결과 확정 대기(FINALIZING)로 둔다.
	 * 진행 중(IN_PROGRESS)에서만 가능하다. 참가자 상태는 참가 도메인이 바꾼다.
	 */
	public void endEarly(LocalDate today) {
		if (status != ChallengeStatus.IN_PROGRESS) {
			log.warn("챌린지 상태 전이 거절: challengeId={}, from={}, to={}", id, status, ChallengeStatus.FINALIZING);
			throw new BusinessException(ChallengeErrorCode.NOT_IN_PROGRESS);
		}
		this.endDate = today;
		this.status = ChallengeStatus.FINALIZING;
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
