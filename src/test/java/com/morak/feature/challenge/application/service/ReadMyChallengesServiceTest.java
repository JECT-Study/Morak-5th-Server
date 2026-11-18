package com.morak.feature.challenge.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.challenge.application.dto.query.MyChallengeFilter;
import com.morak.feature.challenge.application.dto.result.MyChallengeItem;
import com.morak.feature.challenge.application.port.out.ChallengeCertificationReader;
import com.morak.feature.challenge.application.port.out.ChallengeParticipationReader;
import com.morak.feature.challenge.application.port.out.ChallengeParticipationReader.MyParticipationRow;
import com.morak.feature.challenge.application.query.ChallengeReadQuery;
import com.morak.feature.challenge.application.query.ChallengeReadQuery.ChallengeDetail;
import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import com.morak.feature.challenge.domain.enums.ChallengeStatus;
import com.morak.feature.challenge.domain.enums.ChallengeVisibility;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReadMyChallengesServiceTest {

	/** 2026-11-18 00:30 KST. UTC로는 아직 11-17이라 KST 날짜 계산을 함께 검증한다. */
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T15:30:00Z"), ZoneOffset.UTC);

	private final ChallengeParticipationReader participations = mock(ChallengeParticipationReader.class);
	private final ChallengeReadQuery challenges = mock(ChallengeReadQuery.class);
	private final ChallengeCertificationReader certifications = mock(ChallengeCertificationReader.class);
	private final ChallengeCoverImages coverImages = mock(ChallengeCoverImages.class);
	private final ReadMyChallengesService service =
			new ReadMyChallengesService(participations, challenges, certifications, coverImages, CLOCK);

	@Test
	void homeShowsActiveParticipationsInLiveChallengesOnly() {
		when(participations.findMine(1L, Set.of("ACTIVE"))).thenReturn(List.of(
				row(11, 101), row(12, 102), row(13, 103), row(14, 104), row(15, 105), row(16, 106)));
		when(challenges.findByIds(anyList())).thenReturn(List.of(
				challenge(101, ChallengeStatus.RECRUITING),
				challenge(102, ChallengeStatus.START_HELD),
				challenge(103, ChallengeStatus.IN_PROGRESS),
				challenge(104, ChallengeStatus.FINALIZING),
				challenge(105, ChallengeStatus.ENDED)));
		when(certifications.findStats(anyCollection(), any())).thenReturn(Map.of());
		when(coverImages.urls(anyList())).thenReturn(Map.of());

		var page = service.list(1L, MyChallengeFilter.HOME, null, 20);

		// ENDED 챌린지와 삭제된 챌린지(106)는 홈에 없다.
		assertEquals(List.of(11L, 12L, 13L, 14L), page.items().stream().map(MyChallengeItem::participationId).toList());
		assertNull(page.nextCursor());
		assertFalse(page.hasNext());
	}

	@Test
	void certificationFieldsAreNullUntilStatsAreAvailable() {
		when(participations.findMine(1L, Set.of("ACTIVE"))).thenReturn(List.of(row(11, 101)));
		when(challenges.findByIds(anyList())).thenReturn(List.of(challenge(101, ChallengeStatus.IN_PROGRESS)));
		when(certifications.findStats(anyCollection(), any())).thenReturn(Map.of());
		when(coverImages.urls(anyList())).thenReturn(Map.of());

		MyChallengeItem item = service.list(1L, MyChallengeFilter.HOME, null, 20).items().getFirst();

		assertNull(item.teamAchievementRate());
		assertNull(item.todayCertifiedCount());
		assertNull(item.todayTargetCount());
		assertNull(item.myTodayStatus());
		assertNull(item.pendingJudgeCount());
		assertEquals("MEMBER", item.myRole());
		assertEquals("ACTIVE", item.myStatus());
		assertEquals("IN_PROGRESS", item.challengeStatus());
	}

	@Test
	void archivePagesByParticipationIdAndReturnsNextCursor() {
		Set<String> archived = Set.of("EXPELLED", "COMPLETED", "EARLY_ENDED", "DISCARDED");
		when(participations.findMine(1L, archived, 50L, 3)).thenReturn(List.of(
				new MyParticipationRow(40, 201, "MEMBER", "COMPLETED"),
				new MyParticipationRow(30, 202, "HOST", "EXPELLED"),
				new MyParticipationRow(20, 203, "MEMBER", "DISCARDED")));
		when(challenges.findByIds(anyList())).thenReturn(List.of(
				challenge(201, ChallengeStatus.ENDED), challenge(202, ChallengeStatus.IN_PROGRESS)));
		when(certifications.findStats(anyCollection(), any())).thenReturn(Map.of());
		when(coverImages.urls(anyList())).thenReturn(Map.of());

		var page = service.list(1L, MyChallengeFilter.ARCHIVE, 50L, 2);

		assertEquals(List.of(40L, 30L), page.items().stream().map(MyChallengeItem::participationId).toList());
		assertEquals(30L, page.nextCursor());
		assertTrue(page.hasNext());
	}

	@Test
	void archiveRejectsPageSizeOutOfRangeBeforeQuerying() {
		for (int size : new int[] {0, 101}) {
			BusinessException error = assertThrows(BusinessException.class,
					() -> service.list(1L, MyChallengeFilter.ARCHIVE, null, size));
			assertEquals(CommonErrorCode.INVALID_REQUEST, error.getErrorCode());
		}
		verifyNoInteractions(participations, challenges, certifications);
	}

	@Test
	void archiveAcceptsPageSizeBoundaries() {
		when(participations.findMine(any(Long.class), any(), any(), any(Integer.class))).thenReturn(List.of());

		assertTrue(service.list(1L, MyChallengeFilter.ARCHIVE, null, 1).items().isEmpty());
		assertTrue(service.list(1L, MyChallengeFilter.ARCHIVE, null, 100).items().isEmpty());
	}

	@Test
	void progressDayCountsStartDateAsDayOneInKst() {
		LocalDate today = LocalDate.now(CLOCK.withZone(java.time.ZoneId.of("Asia/Seoul")));
		assertEquals(LocalDate.of(2026, 11, 18), today);

		assertEquals(1, ReadMyChallengesService.progressDay(
				challenge(1, ChallengeStatus.IN_PROGRESS, LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 24)), today));
		assertEquals(5, ReadMyChallengesService.progressDay(
				challenge(1, ChallengeStatus.IN_PROGRESS, LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 27)), today));
		// 정산 대기는 종료일 다음 날에도 마지막 일차로 표시한다.
		assertEquals(7, ReadMyChallengesService.progressDay(
				challenge(1, ChallengeStatus.FINALIZING, LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 17)), today));
		assertNull(ReadMyChallengesService.progressDay(challenge(1, ChallengeStatus.RECRUITING), today));
	}

	@Test
	void startHeldNoticeUsesMinimumByVisibilityAndThreeDayDeadline() {
		// 위키 §7: 시작 예정 11/1, 인원 미달 → 11/3 23:59까지 추가 모집.
		ChallengeDetail held = new ChallengeDetail(1, 1, "독서", null, ChallengeVisibility.PUBLIC, ChallengeCategory.READING,
				null, "미션", "규칙", null, ChallengeStatus.START_HELD, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 14),
				5, 2, 0, null);

		var notice = ReadMyChallengesService.startHeld(held);

		assertEquals(2, notice.currentCount());
		assertEquals(3, notice.minCount());
		assertEquals(OffsetDateTime.parse("2026-11-03T23:59:59+09:00"), notice.recruitDeadline());
		assertEquals(2, ReadMyChallengesService.startHeld(new ChallengeDetail(1, 1, "독서", null, ChallengeVisibility.PRIVATE,
				ChallengeCategory.READING, null, "미션", "규칙", null, ChallengeStatus.START_HELD, LocalDate.of(2026, 11, 1),
				LocalDate.of(2026, 11, 14), 5, 1, 0, null)).minCount());
		assertNull(ReadMyChallengesService.startHeld(challenge(1, ChallengeStatus.IN_PROGRESS)));
	}

	private static MyParticipationRow row(long participationId, long challengeId) {
		return new MyParticipationRow(participationId, challengeId, "MEMBER", "ACTIVE");
	}

	private static ChallengeDetail challenge(long id, ChallengeStatus status) {
		return challenge(id, status, LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 27));
	}

	private static ChallengeDetail challenge(long id, ChallengeStatus status, LocalDate start, LocalDate end) {
		return new ChallengeDetail(id, 1, "챌린지 " + id, null, ChallengeVisibility.PUBLIC, ChallengeCategory.STUDY, null,
				"미션", "규칙", null, status, start, end, 5, 3, 0, null);
	}
}
