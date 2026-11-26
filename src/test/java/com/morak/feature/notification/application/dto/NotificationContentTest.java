package com.morak.feature.notification.application.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.common.exception.BusinessException;
import com.morak.feature.notification.application.dto.NotificationContent.ApplicationInvalidated;
import com.morak.feature.notification.application.dto.NotificationContent.CertificationRejected;
import com.morak.feature.notification.application.dto.NotificationContent.ChallengeAutoDiscarded;
import com.morak.feature.notification.application.dto.NotificationContent.ChallengeDiscarded;
import com.morak.feature.notification.application.dto.NotificationContent.DeputyAssigned;
import com.morak.feature.notification.application.dto.NotificationContent.EarlyEnded;
import com.morak.feature.notification.application.dto.NotificationContent.Expelled;
import com.morak.feature.notification.application.dto.NotificationContent.JudgeOverdueWarning;
import com.morak.feature.notification.application.dto.NotificationContent.ParticipationApproved;
import com.morak.feature.notification.application.dto.NotificationContent.ParticipationRejected;
import com.morak.feature.notification.application.dto.NotificationContent.ParticipationRequested;
import com.morak.feature.notification.application.dto.NotificationContent.ScheduleReset;
import com.morak.feature.notification.application.dto.NotificationContent.StartHeld;
import com.morak.feature.notification.domain.entity.NotificationType;
import com.morak.feature.notification.domain.exception.NotificationErrorCode;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class NotificationContentTest {

	private static final String TITLE = "매일 아침 러닝";
	private static final LocalDate DAY = LocalDate.of(2026, 11, 3);

	private static List<NotificationContent> allContents() {
		return List.of(
				new ParticipationApproved(TITLE),
				new ParticipationRejected(TITLE, 1_000L),
				new ApplicationInvalidated(TITLE),
				new StartHeld(TITLE, DAY),
				new ScheduleReset(TITLE, DAY, DAY.plusDays(7)),
				new ChallengeDiscarded(TITLE, 1_000L),
				new ChallengeAutoDiscarded(TITLE, 1_000L),
				new DeputyAssigned(TITLE),
				new JudgeOverdueWarning(TITLE, 2),
				new Expelled(TITLE, 3, 1_000L),
				new EarlyEnded(TITLE, 1_000L),
				new CertificationRejected(TITLE, DAY, 2),
				new ParticipationRequested(TITLE));
	}

	@Test
	void everyNotificationTypeHasExactlyOneTemplate() {
		Set<NotificationType> covered =
				allContents().stream().map(NotificationContent::type).collect(Collectors.toSet());

		assertEquals(EnumSet.allOf(NotificationType.class), covered);
		assertEquals(13, NotificationType.values().length);
	}

	@Test
	void everyTemplateProducesNonBlankTitleAndBody() {
		for (NotificationContent content : allContents()) {
			assertFalse(content.title().isBlank(), content.type() + " title");
			assertFalse(content.body().isBlank(), content.type() + " body");
		}
	}

	@Test
	void requestedTellsTheHostAboutANewApplication() {
		ParticipationRequested content = new ParticipationRequested(TITLE);

		assertEquals(NotificationType.PARTICIPATION_REQUESTED, content.type());
		assertEquals("매일 아침 러닝에 새 참여 신청이 도착했어요", content.body());
	}

	@Test
	void approvedUsesWikiSentence() {
		assertEquals("매일 아침 러닝 참여가 확정됐어요", new ParticipationApproved(TITLE).body());
	}

	@Test
	void rejectedAddsRefundSentenceOnlyWhenPointsWereHeld() {
		String withPoint = new ParticipationRejected(TITLE, 1_500L).body();
		String withoutPoint = new ParticipationRejected(TITLE, null).body();
		String zeroPoint = new ParticipationRejected(TITLE, 0L).body();

		assertTrue(withPoint.contains("걸어 둔 1,500P는 전액 반환됐어요"));
		assertFalse(withoutPoint.contains("P"));
		assertEquals(withoutPoint, zeroPoint);
	}

	@Test
	void discardNoticesAddRefundSentenceOnlyWhenPointsWereHeld() {
		assertTrue(new ChallengeDiscarded(TITLE, 1_000L).body().contains("전액 반환됐어요"));
		assertFalse(new ChallengeDiscarded(TITLE, null).body().contains("반환"));
		assertTrue(new ChallengeAutoDiscarded(TITLE, 5_000L).body().contains("걸어 둔 5,000P는 전액 반환됐어요"));
		assertFalse(new ChallengeAutoDiscarded(TITLE, null).body().contains("반환"));
	}

	@Test
	void startHeldShowsDeadlineAt2359() {
		assertTrue(new StartHeld(TITLE, DAY).body().contains("11월 3일 23:59까지"));
	}

	@Test
	void scheduleResetShowsNewStartAndEndDates() {
		String body = new ScheduleReset(TITLE, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 20)).body();

		assertTrue(body.contains("11월 10일"));
		assertTrue(body.contains("11월 20일"));
	}

	@Test
	void judgeWarningAddsTeamChatNoticeFromSecondWarning() {
		assertFalse(new JudgeOverdueWarning(TITLE, 1).body().contains("팀 채팅"));
		assertTrue(new JudgeOverdueWarning(TITLE, 2).body().contains("팀 채팅"));
		assertTrue(new JudgeOverdueWarning(TITLE, 3).body().contains("팀 채팅"));
	}

	@Test
	void expelledShowsFailCountAndArchiveGuideAndDeductionOnlyForPointChallenge() {
		String withPoint = new Expelled(TITLE, 3, 2_000L).body();
		String withoutPoint = new Expelled(TITLE, 3, null).body();

		assertTrue(withPoint.contains("3회"));
		assertTrue(withPoint.contains("보관함"));
		assertTrue(withPoint.contains("걸어 둔 2,000P는 전액 차감돼요"));
		assertFalse(withoutPoint.contains("차감"));
	}

	@Test
	void expelledDoesNotShowExpelDateBecauseExpulsionIsImmediate() {
		String body = new Expelled(TITLE, 3, 2_000L).body();

		assertFalse(body.contains("예정"));
		assertFalse(body.matches(".*\\d+월 \\d+일.*"));
	}

	@Test
	void earlyEndedUsesWikiSentenceAndAddsSettlementOnlyForPointChallenge() {
		String wikiSentence = "참여자가 2명만 남아 오늘 챌린지가 종료됐어요. 오늘은 인증하지 않아도 되며 실패로 처리되지 않아요";

		assertEquals(wikiSentence, new EarlyEnded(TITLE, null).body());
		assertTrue(new EarlyEnded(TITLE, 1_000L).body().startsWith(wikiSentence));
		assertTrue(new EarlyEnded(TITLE, 1_000L).body().contains("정산"));
		assertFalse(new EarlyEnded(TITLE, null).body().contains("정산"));
		assertEquals(TITLE + " 조기 종료", new EarlyEnded(TITLE, null).title());
	}

	@Test
	void certificationRejectedUsesWikiTitleAndBody() {
		CertificationRejected content = new CertificationRejected(TITLE, LocalDate.of(2026, 11, 17), 2);

		assertEquals("매일 아침 러닝 인증이 승인되지 않았어요", content.title());
		assertEquals("11월 17일 인증이 거절되어 실패 1회로 기록됐어요. 퇴장까지 남은 실패 가능 횟수는 2회예요.", content.body());
		assertEquals(NotificationType.CERTIFICATION_REJECTED, content.type());
	}

	@Test
	void certificationRejectedAllowsZeroRemainingFailsButNotNegative() {
		assertTrue(new CertificationRejected(TITLE, DAY, 0).body().contains("남은 실패 가능 횟수는 0회예요."));
		assertInvalid(() -> new CertificationRejected(TITLE, DAY, -1));
	}

	@Test
	void forbiddenWordsAreNotUsed() {
		List<String> forbidden = List.of("보증금", "예치금", "현금", "충전", "환급", "출금");
		for (NotificationContent content : allContents()) {
			for (String word : forbidden) {
				assertFalse(content.title().contains(word), content.type() + " title has " + word);
				assertFalse(content.body().contains(word), content.type() + " body has " + word);
			}
		}
	}

	@Test
	void missingValuesAreRejected() {
		assertInvalid(() -> new ParticipationApproved(null));
		assertInvalid(() -> new ParticipationApproved(" "));
		assertInvalid(() -> new ParticipationRequested(null));
		assertInvalid(() -> new ParticipationRequested(" "));
		assertInvalid(() -> new StartHeld(TITLE, null));
		assertInvalid(() -> new ScheduleReset(TITLE, DAY, null));
		assertInvalid(() -> new Expelled(TITLE, 0, null));
		assertInvalid(() -> new JudgeOverdueWarning(TITLE, 0));
		assertInvalid(() -> new EarlyEnded(TITLE, -1L));
		assertInvalid(() -> new CertificationRejected(TITLE, null, 1));
		assertInvalid(() -> new ParticipationRejected(TITLE, -1L));
	}

	private static void assertInvalid(org.junit.jupiter.api.function.Executable executable) {
		BusinessException e = assertThrows(BusinessException.class, executable);
		assertEquals(NotificationErrorCode.INVALID_COMMAND, e.getErrorCode());
	}
}
