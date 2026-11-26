package com.morak.feature.certification.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.common.exception.ErrorCode;
import com.morak.feature.certification.application.dto.result.SubmittedCertification;
import com.morak.feature.certification.application.port.out.CertificationImageStorage;
import com.morak.feature.certification.application.port.out.CertificationImageUrls;
import com.morak.feature.certification.application.port.out.CertificationSubmitStore;
import com.morak.feature.certification.application.port.out.CertificationSubmitTargets;
import com.morak.feature.certification.application.port.out.CertificationSubmitTargets.Challenge;
import com.morak.feature.certification.application.port.out.CertificationSubmitTargets.Participant;
import com.morak.feature.certification.domain.exception.CertificationErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class SubmitCertificationServiceTest {

	private static final long USER = 7L;
	private static final long CHALLENGE = 10L;
	private static final long PARTICIPATION = 100L;
	/** 2026-11-24 23:59:59 KST */
	private static final Instant LAST_SECOND = Instant.parse("2026-11-24T14:59:59Z");
	private static final LocalDate DAY = LocalDate.of(2026, 11, 24);
	private static final Instant NEXT_NOON_KST = Instant.parse("2026-11-25T03:00:00Z");
	private static final byte[] PNG = png();

	private final CertificationSubmitTargets targets = mock(CertificationSubmitTargets.class);
	private final CertificationSubmitStore store = mock(CertificationSubmitStore.class);
	private final CertificationImageStorage storage = mock(CertificationImageStorage.class);
	private final CertificationImageUrls urls = mock(CertificationImageUrls.class);

	@Test
	void judgeDueAtIsNextNoonKstWithoutPointsAndNullForAdminReview() {
		assertEquals(NEXT_NOON_KST, SubmitCertificationService.judgeDueAt(DAY, 0));
		assertNull(SubmitCertificationService.judgeDueAt(DAY, 1_000));
	}

	@Test
	void savesTodayInKstUntilTheLastSecond() {
		inProgress(0);
		active();
		when(store.save(anyLong(), anyLong(), any(), any(), any(), anyString())).thenReturn(Optional.of(1L));
		when(urls.resolve(any())).thenReturn(List.of("https://img/1"));

		SubmittedCertification result = service(LAST_SECOND).submit(USER, CHALLENGE, DAY, PNG);

		assertEquals(new SubmittedCertification(1L, DAY, "PENDING", LAST_SECOND, "https://img/1"), result);
		verify(storage).upload(startsWith("certifications/10/"), eq(PNG), eq("image/png"));
		verify(store).save(eq(CHALLENGE), eq(PARTICIPATION), eq(DAY), eq(LAST_SECOND), eq(NEXT_NOON_KST),
				startsWith("certifications/10/"));
	}

	@Test
	void midnightKstCountsAsTheNextDayAndPointChallengesHaveNoJudgeDue() {
		Instant midnight = Instant.parse("2026-11-24T15:00:00Z");
		inProgress(1_000);
		active();
		when(store.save(anyLong(), anyLong(), any(), any(), any(), anyString())).thenReturn(Optional.of(2L));
		when(urls.resolve(any())).thenReturn(List.of());

		SubmittedCertification result = service(midnight).submit(USER, CHALLENGE, DAY.plusDays(1), PNG);

		assertEquals(DAY.plusDays(1), result.certDate());
		assertNull(result.imageUrl());
		verify(store).save(eq(CHALLENGE), eq(PARTICIPATION), eq(DAY.plusDays(1)), eq(midnight), isNull(), anyString());
	}

	@Test
	void rejectsADateOtherThanTodayBeforeUploading() {
		inProgress(0);
		active();
		Instant justAfterMidnight = Instant.parse("2026-11-24T15:00:03Z");

		// 23:59:58에 24일 인증으로 보냈는데 00:00:03에 다 받은 요청, 그리고 앱 시계가 앞선 요청
		assertError(CertificationErrorCode.DATE_MISMATCH, () -> service(justAfterMidnight).submit(USER, CHALLENGE, DAY, PNG));
		assertError(CertificationErrorCode.DATE_MISMATCH,
				() -> service(LAST_SECOND).submit(USER, CHALLENGE, DAY.plusDays(1), PNG));
		verifyNoInteractions(storage);
		verify(store, never()).save(anyLong(), anyLong(), any(), any(), any(), anyString());
	}

	@Test
	void rejectsMissingChallenge() {
		when(targets.findChallenge(CHALLENGE)).thenReturn(Optional.empty());
		assertError(CommonErrorCode.RESOURCE_NOT_FOUND, this::submit);
	}

	@Test
	void rejectsChallengeNotInProgressOrOutsideItsPeriod() {
		active();
		// 조기 종료된 챌린지는 FINALIZING이라 진행 중이 아니다.
		challenge(new Challenge(false, DAY.minusDays(3), DAY, 0));
		assertError(CertificationErrorCode.CHALLENGE_NOT_IN_PROGRESS, this::submit);
		challenge(new Challenge(true, DAY.plusDays(1), DAY.plusDays(7), 0));
		assertError(CertificationErrorCode.CHALLENGE_NOT_IN_PROGRESS, this::submit);
		challenge(new Challenge(true, DAY.minusDays(7), DAY.minusDays(1), 0));
		assertError(CertificationErrorCode.CHALLENGE_NOT_IN_PROGRESS, this::submit);
		verifyNoInteractions(storage);
	}

	@Test
	void rejectsExpelledOrNonParticipants() {
		inProgress(0);
		when(targets.findParticipant(CHALLENGE, USER)).thenReturn(Optional.of(new Participant(PARTICIPATION, false)));
		assertError(CertificationErrorCode.NOT_ACTIVE_PARTICIPANT, this::submit);
		when(targets.findParticipant(CHALLENGE, USER)).thenReturn(Optional.empty());
		assertError(CertificationErrorCode.NOT_ACTIVE_PARTICIPANT, this::submit);
		verifyNoInteractions(storage);
	}

	@Test
	void rejectsSecondSubmissionOfTheDayBeforeUploading() {
		inProgress(0);
		active();
		when(store.exists(PARTICIPATION, DAY)).thenReturn(true);
		assertError(CertificationErrorCode.ALREADY_SUBMITTED, this::submit);
		verifyNoInteractions(storage);
	}

	@Test
	void concurrentDuplicateRemovesTheUploadedImage() {
		inProgress(0);
		active();
		when(store.save(anyLong(), anyLong(), any(), any(), any(), anyString())).thenReturn(Optional.empty());

		assertError(CertificationErrorCode.ALREADY_SUBMITTED, this::submit);
		verify(storage).delete(startsWith("certifications/10/"));
	}

	@Test
	void saveFailureRemovesTheUploadedImageEvenIfCleanupFails() {
		inProgress(0);
		active();
		when(store.save(anyLong(), anyLong(), any(), any(), any(), anyString()))
				.thenThrow(new IllegalStateException("db"));
		doThrow(new RuntimeException("storage")).when(storage).delete(anyString());

		IllegalStateException failure = assertThrows(IllegalStateException.class, this::submit);
		assertEquals("db", failure.getMessage());
		verify(storage).delete(startsWith("certifications/10/"));
	}

	@Test
	void rejectsInvalidOrTooLargeImagesAndMissingStorage() {
		inProgress(0);
		active();
		SubmitCertificationService service = service(LAST_SECOND);
		assertError(CertificationErrorCode.INVALID_IMAGE, () -> service.submit(USER, CHALLENGE, DAY, "not an image".getBytes()));
		assertError(CertificationErrorCode.IMAGE_TOO_LARGE,
				() -> service.submit(USER, CHALLENGE, DAY, new byte[SubmitCertificationService.MAX_IMAGE_BYTES + 1]));
		SubmitCertificationService noStorage = new SubmitCertificationService(
				targets, store, Optional.empty(), urls, Clock.fixed(LAST_SECOND, ZoneOffset.UTC));
		assertError(CertificationErrorCode.IMAGE_STORAGE_UNAVAILABLE, () -> noStorage.submit(USER, CHALLENGE, DAY, PNG));
		verifyNoInteractions(storage);
		verify(store, never()).save(anyLong(), anyLong(), any(), any(), any(), anyString());
	}

	private void inProgress(int pointAmount) {
		challenge(new Challenge(true, DAY.minusDays(3), DAY.plusDays(3), pointAmount));
	}

	private void challenge(Challenge challenge) {
		when(targets.findChallenge(CHALLENGE)).thenReturn(Optional.of(challenge));
	}

	private void active() {
		when(targets.findParticipant(CHALLENGE, USER)).thenReturn(Optional.of(new Participant(PARTICIPATION, true)));
	}

	private SubmitCertificationService service(Instant now) {
		return new SubmitCertificationService(targets, store, Optional.of(storage), urls, Clock.fixed(now, ZoneOffset.UTC));
	}

	private void submit() {
		service(LAST_SECOND).submit(USER, CHALLENGE, DAY, PNG);
	}

	private static byte[] png() {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
			return out.toByteArray();
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private static void assertError(ErrorCode expected, Executable executable) {
		BusinessException exception = assertThrows(BusinessException.class, executable);
		assertEquals(expected, exception.getErrorCode());
	}
}
