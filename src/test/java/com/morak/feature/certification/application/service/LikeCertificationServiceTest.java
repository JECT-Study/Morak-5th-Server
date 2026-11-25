package com.morak.feature.certification.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.certification.application.dto.result.CertificationLike;
import com.morak.feature.certification.application.port.out.CertificationFeedTargets;
import com.morak.feature.certification.application.port.out.CertificationFeedTargets.Viewer;
import com.morak.feature.certification.application.port.out.CertificationLikeStore;
import com.morak.feature.certification.domain.exception.CertificationErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LikeCertificationServiceTest {

	private static final long USER = 7L;
	private static final long CHALLENGE = 10L;
	private static final long CERT = 50L;
	private static final Instant NOW = Instant.parse("2026-11-25T03:00:00Z");

	private final CertificationFeedTargets targets = mock(CertificationFeedTargets.class);
	private final CertificationLikeStore store = mock(CertificationLikeStore.class);
	private final LikeCertificationService service =
			new LikeCertificationService(targets, store, Clock.fixed(NOW, ZoneOffset.UTC));

	@ParameterizedTest
	@ValueSource(strings = {"APPROVED", "AUTO_APPROVED"})
	void likesAnApprovedCertificationAndReturnsTheCount(String status) {
		member("ACTIVE");
		when(store.findStatus(CHALLENGE, CERT)).thenReturn(Optional.of(status));
		when(store.count(CERT)).thenReturn(3);

		assertEquals(new CertificationLike(CERT, true, 3), service.like(USER, CHALLENGE, CERT));
		verify(store).like(CERT, USER, NOW);
	}

	@Test
	void unlikesAndReturnsTheCount() {
		member("COMPLETED");
		when(store.findStatus(CHALLENGE, CERT)).thenReturn(Optional.of("APPROVED"));
		when(store.count(CERT)).thenReturn(0);

		assertEquals(new CertificationLike(CERT, false, 0), service.unlike(USER, CHALLENGE, CERT));
		verify(store).unlike(CERT, USER);
	}

	@ParameterizedTest
	@ValueSource(strings = {"PENDING", "REJECTED", "FAILED"})
	void rejectsACertificationThatIsNotApproved(String status) {
		member("ACTIVE");
		when(store.findStatus(CHALLENGE, CERT)).thenReturn(Optional.of(status));

		assertError(CertificationErrorCode.LIKE_TARGET_NOT_FOUND);
		verify(store, never()).like(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void rejectsACertificationOfAnotherChallenge() {
		member("ACTIVE");
		when(store.findStatus(CHALLENGE, CERT)).thenReturn(Optional.empty());

		assertError(CertificationErrorCode.LIKE_TARGET_NOT_FOUND);
	}

	@ParameterizedTest
	@ValueSource(strings = {"PENDING", "EXPELLED", "EXPEL_NOTICED"})
	void rejectsUsersWhoCannotSeeTheFeed(String status) {
		member(status);

		assertError(CertificationErrorCode.LIKE_NOT_MEMBER);
		verify(store, never()).findStatus(anyLong(), anyLong());
	}

	@Test
	void rejectsNonParticipants() {
		when(targets.challengeExists(CHALLENGE)).thenReturn(true);
		when(targets.findViewer(CHALLENGE, USER)).thenReturn(Optional.empty());

		assertError(CertificationErrorCode.LIKE_NOT_MEMBER);
	}

	@Test
	void returnsNotFoundForAMissingChallenge() {
		when(targets.challengeExists(CHALLENGE)).thenReturn(false);

		assertError(CommonErrorCode.RESOURCE_NOT_FOUND);
	}

	private void member(String status) {
		when(targets.challengeExists(CHALLENGE)).thenReturn(true);
		when(targets.findViewer(CHALLENGE, USER)).thenReturn(Optional.of(new Viewer(100L, status)));
	}

	private void assertError(Object code) {
		BusinessException like = assertThrows(BusinessException.class, () -> service.like(USER, CHALLENGE, CERT));
		assertEquals(code, like.getErrorCode());
		BusinessException unlike = assertThrows(BusinessException.class, () -> service.unlike(USER, CHALLENGE, CERT));
		assertEquals(code, unlike.getErrorCode());
	}
}
