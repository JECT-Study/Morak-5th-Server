package com.morak.feature.certification.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.morak.feature.certification.application.port.out.CertificationCardStore;
import com.morak.feature.certification.application.port.out.CertificationCardStore.CardRow;
import com.morak.feature.certification.application.port.out.CertificationCardStore.PhotoRow;
import com.morak.feature.certification.application.port.out.CertificationImageUrls;
import com.morak.feature.certification.application.query.CertificationCardQuery.CardStats;
import com.morak.feature.certification.application.query.CertificationCardQuery.TodayPhoto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReadCertificationCardsServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

	private final CertificationCardStore store = mock(CertificationCardStore.class);
	private final CertificationImageUrls urls = mock(CertificationImageUrls.class);
	private final ReadCertificationCardsService service = new ReadCertificationCardsService(store, urls);

	@Test
	void teamAchievementRateUsesFailuresOverTargetWithHalfUpTenths() {
		// 위키 §7: 5명 × 14일, A가 4일차에 퇴장 → 대상 59, 성공 56 → 94.92% → 94.9
		assertEquals(new BigDecimal("94.9"), ReadCertificationCardsService.teamAchievementRate(59L, 3));
		// 93.75 → 93.8 (half-up)
		assertEquals(new BigDecimal("93.8"), ReadCertificationCardsService.teamAchievementRate(16L, 1));
		assertEquals(new BigDecimal("100.0"), ReadCertificationCardsService.teamAchievementRate(70L, 0));
		assertEquals(new BigDecimal("90.0"), ReadCertificationCardsService.teamAchievementRate(70L, 7));
		assertNull(ReadCertificationCardsService.teamAchievementRate(null, 0));
		assertNull(ReadCertificationCardsService.teamAchievementRate(0L, 0));
	}

	@Test
	void buildsTodayTeamWithSignedPhotosOnlyOnCertificationDays() {
		Instant at = Instant.parse("2026-11-25T01:00:00Z");
		when(store.findCards(List.of(1L, 2L), TODAY)).thenReturn(List.of(
				new CardRow(1L, 10L, 59L, 3, true, 4, 2, "PENDING", 1),
				new CardRow(2L, 20L, null, 0, false, 0, 0, null, 0)));
		when(store.findApprovedPhotos(List.of(10L), TODAY)).thenReturn(List.of(new PhotoRow(10L, 7L, "k1", at)));
		when(urls.resolveByKey(List.of("k1"))).thenReturn(Map.of("k1", "https://signed/k1"));

		Map<Long, CardStats> stats = service.findCardStats(List.of(1L, 2L), TODAY);

		CardStats open = stats.get(1L);
		assertEquals(new BigDecimal("94.9"), open.teamAchievementRate());
		assertEquals(2, open.todayTeam().certifiedCount());
		assertEquals(4, open.todayTeam().targetCount());
		assertEquals(List.of(new TodayPhoto(7L, "https://signed/k1", at)), open.todayTeam().photos());
		assertEquals("PENDING", open.myTodayStatus());
		assertEquals(1, open.pendingJudgeCount());
		CardStats notStarted = stats.get(2L);
		assertNull(notStarted.teamAchievementRate());
		assertNull(notStarted.todayTeam());
	}

	@Test
	void skipsPhotoQueryWhenNoCardIsOpenToday() {
		when(store.findCards(List.of(2L), TODAY)).thenReturn(List.of(new CardRow(2L, 20L, 10L, 0, false, 0, 0, null, 0)));
		when(urls.resolveByKey(List.of())).thenReturn(Map.of());

		service.findCardStats(List.of(2L), TODAY);

		verify(store, never()).findApprovedPhotos(anyList(), any());
	}
}
