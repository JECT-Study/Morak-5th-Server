package com.morak.feature.notification.application.dto;

import com.morak.common.exception.BusinessException;
import com.morak.feature.notification.domain.entity.NotificationType;
import com.morak.feature.notification.domain.exception.NotificationErrorCode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 알림 종류별 문구 템플릿. 호출하는 도메인은 문구가 아니라 값(챌린지명, 포인트, 날짜 등)만 넘기고,
 * 문구는 이 파일에서만 정한다. 종류마다 필요한 값이 다르므로 종류별 record로 나눠 필수 값을 컴파일 시점에 강제한다.
 *
 * 포인트 문장은 pointAmount가 1 이상일 때만 붙인다. 포인트를 건 사람에게만 붙인다는 위키 §4.8 규칙을
 * "포인트를 걸지 않은 수신자는 null 또는 0을 넘긴다"로 구현한 것이다.
 *
 * TODO: 위키에는 #1·#2의 문구와 포인트 반환 문장만 있다. 나머지 문구와 제목은 비고 설명으로 만든 임시 문구이므로
 * FRD 알림 문구 원문이 확정되면 교체한다.
 */
public sealed interface NotificationContent {

	DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREA);

	NotificationType type();

	/** 알림함 목록에 보이는 짧은 제목. */
	String title();

	/** 알림 본문. 포인트 문장이 있으면 본문 끝에 붙는다. */
	String body();

	/** #1 참가 신청 승인 → 신청자 */
	record ParticipationApproved(String challengeTitle) implements NotificationContent {

		public ParticipationApproved {
			requireText(challengeTitle, "challengeTitle");
		}

		@Override public NotificationType type() { return NotificationType.PARTICIPATION_APPROVED; }
		@Override public String title() { return "참여 확정"; }
		@Override public String body() { return challengeTitle + " 참여가 확정됐어요"; }
	}

	/** #2 참가 신청 거절 → 신청자. 정원 마감 자동 거절도 같은 문구를 쓴다. */
	record ParticipationRejected(String challengeTitle, Long pointAmount) implements NotificationContent {

		public ParticipationRejected {
			requireText(challengeTitle, "challengeTitle");
			requirePointAmount(pointAmount);
		}

		@Override public NotificationType type() { return NotificationType.PARTICIPATION_REJECTED; }
		@Override public String title() { return "참여 신청 거절"; }
		@Override public String body() { return challengeTitle + " 참여 신청이 거절됐어요" + refundSentence(pointAmount); }
	}

	/** #3 참여 한도 초과로 신청 무효화 → 해당 신청을 받은 방장 */
	record ApplicationInvalidated(String challengeTitle) implements NotificationContent {

		public ApplicationInvalidated {
			requireText(challengeTitle, "challengeTitle");
		}

		@Override public NotificationType type() { return NotificationType.APPLICATION_INVALIDATED; }
		@Override public String title() { return "참여 신청 무효"; }
		@Override public String body() { return challengeTitle + " 참여 신청이 참여 한도 초과로 무효 처리됐어요"; }
	}

	/** #4 예정 시작일 최소 인원 미달 → 방장. 선택 기한은 해당 날짜의 23:59다. */
	record StartHeld(String challengeTitle, LocalDate deadlineDate) implements NotificationContent {

		public StartHeld {
			requireText(challengeTitle, "challengeTitle");
			requireNonNull(deadlineDate, "deadlineDate");
		}

		@Override public NotificationType type() { return NotificationType.START_HELD; }
		@Override public String title() { return "시작 보류"; }
		@Override public String body() {
			return challengeTitle + " 최소 인원이 모이지 않아 시작이 보류됐어요. "
					+ MONTH_DAY.format(deadlineDate) + " 23:59까지 선택해 주세요";
		}
	}

	/** #5 방장이 기간 재설정 → 다른 시작 대기 참여자 */
	record ScheduleReset(String challengeTitle, LocalDate startDate, LocalDate endDate)
			implements NotificationContent {

		public ScheduleReset {
			requireText(challengeTitle, "challengeTitle");
			requireNonNull(startDate, "startDate");
			requireNonNull(endDate, "endDate");
		}

		@Override public NotificationType type() { return NotificationType.SCHEDULE_RESET; }
		@Override public String title() { return "기간 재설정"; }
		@Override public String body() {
			return challengeTitle + " 기간이 " + MONTH_DAY.format(startDate) + "부터 "
					+ MONTH_DAY.format(endDate) + "까지로 바뀌었어요";
		}
	}

	/** #6 방장이 시작 전 폐기 → 다른 시작 대기 참여자 */
	record ChallengeDiscarded(String challengeTitle, Long pointAmount) implements NotificationContent {

		public ChallengeDiscarded {
			requireText(challengeTitle, "challengeTitle");
			requirePointAmount(pointAmount);
		}

		@Override public NotificationType type() { return NotificationType.CHALLENGE_DISCARDED; }
		@Override public String title() { return "챌린지 폐기"; }
		@Override public String body() { return challengeTitle + " 챌린지가 폐기됐어요" + refundSentence(pointAmount); }
	}

	/** #7 기한 내 미선택 자동 폐기 → 방장과 시작 대기 참여자 전체 */
	record ChallengeAutoDiscarded(String challengeTitle, Long pointAmount) implements NotificationContent {

		public ChallengeAutoDiscarded {
			requireText(challengeTitle, "challengeTitle");
			requirePointAmount(pointAmount);
		}

		@Override public NotificationType type() { return NotificationType.CHALLENGE_AUTO_DISCARDED; }
		@Override public String title() { return "챌린지 자동 폐기"; }
		@Override public String body() {
			return challengeTitle + " 챌린지가 기한 안에 시작되지 않아 자동으로 폐기됐어요" + refundSentence(pointAmount);
		}
	}

	/** #8 첫 부방장 지정 → 첫 합류자. 포인트 챌린지 여부에 따른 본문 구분은 FRD 문구 확정 때 추가한다. */
	record DeputyAssigned(String challengeTitle) implements NotificationContent {

		public DeputyAssigned {
			requireText(challengeTitle, "challengeTitle");
		}

		@Override public NotificationType type() { return NotificationType.DEPUTY_ASSIGNED; }
		@Override public String title() { return "부방장 지정"; }
		@Override public String body() { return challengeTitle + " 챌린지의 부방장으로 지정됐어요. 눌러서 역할 안내를 확인해 보세요"; }
	}

	/** #9 인증 판정 기한 초과 경고 → 기한 내 판정 안 한 방장·부방장. 누적 2회부터 팀 채팅 안내 문장을 붙인다. */
	record JudgeOverdueWarning(String challengeTitle, int warningCount) implements NotificationContent {

		public static final int TEAM_CHAT_NOTICE_FROM = 2;

		public JudgeOverdueWarning {
			requireText(challengeTitle, "challengeTitle");
			if (warningCount < 1) {
				throw invalid("warningCount=" + warningCount);
			}
		}

		@Override public NotificationType type() { return NotificationType.JUDGE_OVERDUE_WARNING; }
		@Override public String title() { return "인증 판정 기한 초과"; }
		@Override public String body() {
			String base = challengeTitle + " 인증 판정 기한이 지났어요. 경고 " + warningCount + "회가 누적됐어요";
			return warningCount >= TEAM_CHAT_NOTICE_FROM ? base + ". 팀 채팅에도 안내가 올라갔어요" : base;
		}
	}

	/** #10 개인 실패 누적으로 퇴장 예정 → 퇴장 대상자. 포인트 챌린지는 전액 차감 문장을 붙인다. */
	record ExpelNoticed(String challengeTitle, int failCount, LocalDate expelDate, Long pointAmount)
			implements NotificationContent {

		public ExpelNoticed {
			requireText(challengeTitle, "challengeTitle");
			requireNonNull(expelDate, "expelDate");
			requirePointAmount(pointAmount);
			if (failCount < 1) {
				throw invalid("failCount=" + failCount);
			}
		}

		@Override public NotificationType type() { return NotificationType.EXPEL_NOTICED; }
		@Override public String title() { return "퇴장 예정"; }
		@Override public String body() {
			String base = challengeTitle + " 인증 실패가 " + failCount + "회 쌓여 "
					+ MONTH_DAY.format(expelDate) + "에 퇴장될 예정이에요";
			return hasPoint(pointAmount) ? base + String.format(" 걸어 둔 %,dP는 전액 차감돼요", pointAmount) : base;
		}
	}

	/** #11 공개 챌린지 조기 종료 예정 → 남게 될 두 참여자. 포인트 챌린지는 정산 문장을 붙인다. */
	record EarlyEndNoticed(String challengeTitle, LocalDate endDate, Long pointAmount) implements NotificationContent {

		public EarlyEndNoticed {
			requireText(challengeTitle, "challengeTitle");
			requireNonNull(endDate, "endDate");
			requirePointAmount(pointAmount);
		}

		@Override public NotificationType type() { return NotificationType.EARLY_END_NOTICED; }
		@Override public String title() { return "조기 종료 예정"; }
		@Override public String body() {
			String base = challengeTitle + " 챌린지가 " + MONTH_DAY.format(endDate) + "에 조기 종료될 예정이에요";
			return hasPoint(pointAmount)
					? base + ". 포인트는 개인 성공률대로 정산되고 최대 100%까지 돌려받으며 추가 보상은 없어요"
					: base;
		}
	}

	private static boolean hasPoint(Long pointAmount) {
		return pointAmount != null && pointAmount > 0;
	}

	/** 포인트를 건 사람에게만 붙는 반환 문장 (위키 §4.8 #2). */
	private static String refundSentence(Long pointAmount) {
		return hasPoint(pointAmount) ? String.format(". 걸어 둔 %,dP는 전액 반환됐어요", pointAmount) : "";
	}

	private static void requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw invalid(name + " is blank");
		}
	}

	private static void requireNonNull(Object value, String name) {
		if (value == null) {
			throw invalid(name + " is null");
		}
	}

	private static void requirePointAmount(Long pointAmount) {
		if (pointAmount != null && pointAmount < 0) {
			throw invalid("pointAmount=" + pointAmount);
		}
	}

	private static BusinessException invalid(String detail) {
		return new BusinessException(NotificationErrorCode.INVALID_COMMAND, detail);
	}
}
