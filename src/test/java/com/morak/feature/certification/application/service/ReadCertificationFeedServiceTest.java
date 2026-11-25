package com.morak.feature.certification.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.certification.application.dto.result.CertificationFeed;
import com.morak.feature.certification.application.port.out.CertificationFeedStore;
import com.morak.feature.certification.application.port.out.CertificationFeedStore.FeedRow;
import com.morak.feature.certification.application.port.out.CertificationFeedTargets;
import com.morak.feature.certification.application.port.out.CertificationFeedTargets.Submitter;
import com.morak.feature.certification.application.port.out.CertificationFeedTargets.Viewer;
import com.morak.feature.certification.application.port.out.CertificationImageUrls;
import com.morak.feature.certification.domain.exception.CertificationErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReadCertificationFeedServiceTest {

	private static final long USER = 7L;
	private static final long CHALLENGE = 10L;
	private static final long ME = 100L;
	private static final long TEAMMATE = 101L;
	/** 2026-11-25 00:30 KST: UTC로는 아직 11/24지만 오늘은 11/25다. */
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T15:30:00Z"), ZoneOffset.UTC);
	private static final LocalDate TODAY_KST = LocalDate.of(2026, 11, 25);

	private final CertificationFeedTargets targets = mock(CertificationFeedTargets.class);
	private final CertificationFeedStore store = mock(CertificationFeedStore.class);
	private final CertificationImageUrls urls = mock(CertificationImageUrls.class);
	private final ReadCertificationFeedService service = new ReadCertificationFeedService(targets, store, urls, CLOCK);

	@Test
	void readsTodaysFeedWithSignedUrlsAndMarksMyCertification() {
		when(targets.challengeExists(CHALLENGE)).thenReturn(true);
		when(targets.findViewer(CHALLENGE, USER)).thenReturn(Optional.of(new Viewer(ME, "ACTIVE")));
		Instant first = Instant.parse("2026-11-25T01:00:00Z");
		Instant second = Instant.parse("2026-11-25T02:00:00Z");
		when(store.find(CHALLENGE, TODAY_KST, ME)).thenReturn(List.of(
				new FeedRow(1L, TEAMMATE, "APPROVED", first, "k1"),
				new FeedRow(2L, ME, "PENDING", second, "k2")));
		when(targets.findSubmitters(List.of(TEAMMATE, ME))).thenReturn(Map.of(
				TEAMMATE, new Submitter(8L, "MEMBER", "팀원", "https://p/8"),
				ME, new Submitter(USER, "HOST", "나", null)));
		// k2는 서명에 실패한 키다.
		when(urls.resolveByKey(List.of("k1", "k2"))).thenReturn(Map.of("k1", "https://signed/k1"));

		CertificationFeed feed = service.read(USER, CHALLENGE, null);

		assertEquals(TODAY_KST, feed.certDate());
		assertEquals(List.of(
				new CertificationFeed.Item(1L, 8L, "팀원", "https://p/8", "MEMBER", "APPROVED", first,
						"https://signed/k1", false),
				new CertificationFeed.Item(2L, USER, "나", null, "HOST", "PENDING", second, null, true)),
				feed.items());
	}

	@Test
	void readsTheRequestedDate() {
		LocalDate day = LocalDate.of(2026, 11, 20);
		when(targets.challengeExists(CHALLENGE)).thenReturn(true);
		when(targets.findViewer(CHALLENGE, USER)).thenReturn(Optional.of(new Viewer(ME, "COMPLETED")));
		when(store.find(CHALLENGE, day, ME)).thenReturn(List.of());
		when(targets.findSubmitters(any())).thenReturn(Map.of());
		when(urls.resolveByKey(any())).thenReturn(Map.of());

		CertificationFeed feed = service.read(USER, CHALLENGE, day);

		assertEquals(day, feed.certDate());
		assertTrue(feed.items().isEmpty());
		verify(store).find(CHALLENGE, day, ME);
	}

	@ParameterizedTest
	@ValueSource(strings = {"ACTIVE", "COMPLETED", "EARLY_ENDED"})
	void allowsMembersWhoAreInOrFinishedTheChallenge(String status) {
		when(targets.challengeExists(CHALLENGE)).thenReturn(true);
		when(targets.findViewer(CHALLENGE, USER)).thenReturn(Optional.of(new Viewer(ME, status)));
		when(store.find(any(Long.class), any(), any(Long.class))).thenReturn(List.of());
		when(targets.findSubmitters(any())).thenReturn(Map.of());
		when(urls.resolveByKey(any())).thenReturn(Map.of());

		assertEquals(TODAY_KST, service.read(USER, CHALLENGE, null).certDate());
	}

	@Test
	void rejectsUsersWhoAreNotConfirmedMembersBeforeSigningAnything() {
		when(targets.challengeExists(CHALLENGE)).thenReturn(true);
		when(targets.findViewer(CHALLENGE, USER)).thenReturn(Optional.empty());

		BusinessException exception = assertThrows(BusinessException.class, () -> service.read(USER, CHALLENGE, null));

		assertEquals(CertificationErrorCode.FEED_NOT_MEMBER, exception.getErrorCode());
		verifyNoInteractions(store, urls);
	}

	@Test
	void rejectsALeftoverExpelNoticedMember() {
		// findMembers는 폐지된 EXPEL_NOTICED도 돌려준다. 퇴장자 열람 범위(O-16)가 정해지기 전까지 막는다.
		when(targets.challengeExists(CHALLENGE)).thenReturn(true);
		when(targets.findViewer(CHALLENGE, USER)).thenReturn(Optional.of(new Viewer(ME, "EXPEL_NOTICED")));

		BusinessException exception = assertThrows(BusinessException.class, () -> service.read(USER, CHALLENGE, null));

		assertEquals(CertificationErrorCode.FEED_NOT_MEMBER, exception.getErrorCode());
		verifyNoInteractions(store, urls);
	}

	@Test
	void returnsNotFoundForAMissingChallenge() {
		when(targets.challengeExists(CHALLENGE)).thenReturn(false);

		BusinessException exception = assertThrows(BusinessException.class, () -> service.read(USER, CHALLENGE, null));

		assertEquals(CommonErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
	}

	@Test
	void skipsARowWhoseParticipationIsGone() {
		when(targets.challengeExists(CHALLENGE)).thenReturn(true);
		when(targets.findViewer(CHALLENGE, USER)).thenReturn(Optional.of(new Viewer(ME, "ACTIVE")));
		when(store.find(CHALLENGE, TODAY_KST, ME)).thenReturn(
				List.of(new FeedRow(1L, TEAMMATE, "APPROVED", Instant.EPOCH, null)));
		when(targets.findSubmitters(List.of(TEAMMATE))).thenReturn(Map.of());
		when(urls.resolveByKey(List.of())).thenReturn(Map.of());

		assertTrue(service.read(USER, CHALLENGE, null).items().isEmpty());
	}
}
