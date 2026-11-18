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
 * 위키(FRD 11/1)에 문구 원문이 있는 것은 #1, #2, #11 본문, #12 제목·본문, 포인트 반환 문장이다.
 * TODO: #3~#9, #10의 제목·본문과 #11의 제목·정산 문장은 위키 비고 설명으로 만든 임시 문구이므로
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

	/**
	 * #10 개인 실패 누적으로 퇴장 → 퇴장 대상자. 공지와 동시에 퇴장하므로 퇴장일은 표시하지 않는다.
	 * 실패 횟수와 보관함 안내를 보여 주고, 포인트 챌린지는 전액 차감 문장을 붙인다.
	 */
	record Expelled(String challengeTitle, int failCount, Long pointAmount) implements NotificationContent {

		public Expelled {
			requireText(challengeTitle, "challengeTitle");
			requirePointAmount(pointAmount);
			if (failCount < 1) {
				throw invalid("failCount=" + failCount);
			}
		}

		@Override public NotificationType type() { return NotificationType.EXPELLED; }
		@Override public String title() { return "챌린지 퇴장"; }
		@Override public String body() {
			String base = challengeTitle + " 인증 실패가 " + failCount
					+ "회 쌓여 퇴장됐어요. 지금까지의 인증과 채팅 기록은 보관함에서 볼 수 있어요";
			return hasPoint(pointAmount) ? base + String.format(". 걸어 둔 %,dP는 전액 차감돼요", pointAmount) : base;
		}
	}

	/**
	 * #11 공개 챌린지 조기 종료 → 남은 두 참여자. 퇴장한 날 종료되므로 종료일은 항상 오늘이라 날짜를 받지 않는다.
	 * 포인트 챌린지는 정산 문장을 붙인다.
	 */
	record EarlyEnded(String challengeTitle, Long pointAmount) implements NotificationContent {

		public EarlyEnded {
			requireText(challengeTitle, "challengeTitle");
			requirePointAmount(pointAmount);
		}

		@Override public NotificationType type() { return NotificationType.EARLY_ENDED; }
		@Override public String title() { return challengeTitle + " 조기 종료"; }
		@Override public String body() {
			String base = "참여자가 2명만 남아 오늘 챌린지가 종료됐어요. 오늘은 인증하지 않아도 되며 실패로 처리되지 않아요";
			return hasPoint(pointAmount)
					? base + ". 포인트는 개인 성공률대로 정산되고 최대 100%까지 돌려받으며 추가 보상은 없어요"
					: base;
		}
	}

	/**
	 * #12 인증 거절 → 인증이 거절된 참여자. 사유는 표시하지 않는다.
	 * remainingFailCount는 퇴장 기준에서 누적 실패 횟수를 뺀 남은 실패 가능 횟수다.
	 */
	record CertificationRejected(String challengeTitle, LocalDate certDate, int remainingFailCount)
			implements NotificationContent {

		public CertificationRejected {
			requireText(challengeTitle, "challengeTitle");
			requireNonNull(certDate, "certDate");
			if (remainingFailCount < 0) {
				throw invalid("remainingFailCount=" + remainingFailCount);
			}
		}

		@Override public NotificationType type() { return NotificationType.CERTIFICATION_REJECTED; }
		@Override public String title() { return challengeTitle + " 인증이 승인되지 않았어요"; }
		@Override public String body() {
			return MONTH_DAY.format(certDate) + " 인증이 거절되어 실패 1회로 기록됐어요. 퇴장까지 남은 실패 가능 횟수는 "
					+ remainingFailCount + "회예요.";
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
