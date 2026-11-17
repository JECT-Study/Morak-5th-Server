package com.morak.feature.notification.application.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.common.exception.BusinessException;
import com.morak.feature.notification.application.dto.NotificationContent.ApplicationInvalidated;
import com.morak.feature.notification.application.dto.NotificationContent.ChallengeAutoDiscarded;
import com.morak.feature.notification.application.dto.NotificationContent.ChallengeDiscarded;
import com.morak.feature.notification.application.dto.NotificationContent.DeputyAssigned;
import com.morak.feature.notification.application.dto.NotificationContent.EarlyEndNoticed;
import com.morak.feature.notification.application.dto.NotificationContent.ExpelNoticed;
import com.morak.feature.notification.application.dto.NotificationContent.JudgeOverdueWarning;
import com.morak.feature.notification.application.dto.NotificationContent.ParticipationApproved;
import com.morak.feature.notification.application.dto.NotificationContent.ParticipationRejected;
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
				new ExpelNoticed(TITLE, 3, DAY, 1_000L),
				new EarlyEndNoticed(TITLE, DAY, 1_000L));
	}

	@Test
	void everyNotificationTypeHasExactlyOneTemplate() {
		Set<NotificationType> covered =
				allContents().stream().map(NotificationContent::type).collect(Collectors.toSet());

		assertEquals(EnumSet.allOf(NotificationType.class), covered);
		assertEquals(11, NotificationType.values().length);
	}

	@Test
	void everyTemplateProducesNonBlankTitleAndBody() {
		for (NotificationContent content : allContents()) {
			assertFalse(content.title().isBlank(), content.type() + " title");
			assertFalse(content.body().isBlank(), content.type() + " body");
		}
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
	void expelNoticeShowsFailCountAndExpelDateAndDeductionOnlyForPointChallenge() {
		String withPoint = new ExpelNoticed(TITLE, 3, LocalDate.of(2026, 11, 18), 2_000L).body();
		String withoutPoint = new ExpelNoticed(TITLE, 3, LocalDate.of(2026, 11, 18), null).body();

		assertTrue(withPoint.contains("3회"));
		assertTrue(withPoint.contains("11월 18일"));
		assertTrue(withPoint.contains("걸어 둔 2,000P는 전액 차감돼요"));
		assertFalse(withoutPoint.contains("차감"));
	}

	@Test
	void earlyEndNoticeAddsSettlementSentenceOnlyForPointChallenge() {
		assertTrue(new EarlyEndNoticed(TITLE, DAY, 1_000L).body().contains("정산"));
		assertFalse(new EarlyEndNoticed(TITLE, DAY, null).body().contains("정산"));
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
		assertInvalid(() -> new StartHeld(TITLE, null));
		assertInvalid(() -> new ScheduleReset(TITLE, DAY, null));
		assertInvalid(() -> new ExpelNoticed(TITLE, 0, DAY, null));
		assertInvalid(() -> new ExpelNoticed(TITLE, 1, null, null));
		assertInvalid(() -> new JudgeOverdueWarning(TITLE, 0));
		assertInvalid(() -> new EarlyEndNoticed(TITLE, null, null));
		assertInvalid(() -> new ParticipationRejected(TITLE, -1L));
	}

	private static void assertInvalid(org.junit.jupiter.api.function.Executable executable) {
		BusinessException e = assertThrows(BusinessException.class, executable);
		assertEquals(NotificationErrorCode.INVALID_COMMAND, e.getErrorCode());
	}
}
