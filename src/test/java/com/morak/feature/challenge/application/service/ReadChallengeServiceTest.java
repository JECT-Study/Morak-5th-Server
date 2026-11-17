package com.morak.feature.challenge.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.challenge.application.dto.query.ListChallengesQuery;
import com.morak.feature.challenge.application.port.out.ChallengeParticipationReader;
import com.morak.feature.challenge.application.port.out.InviteCodeAttemptLimiter;
import com.morak.feature.challenge.application.query.ChallengeReadQuery;
import com.morak.feature.challenge.application.query.ChallengeReadQuery.ChallengeDetail;
import com.morak.feature.challenge.application.query.ChallengeReadQuery.ChallengeSummary;
import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import com.morak.feature.challenge.domain.enums.ChallengeStatus;
import com.morak.feature.challenge.domain.enums.ChallengeVisibility;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReadChallengeServiceTest {

	private final ChallengeReadQuery challenges = mock(ChallengeReadQuery.class);
	private final ChallengeParticipationReader participations = mock(ChallengeParticipationReader.class);
	private final ChallengeCoverImages coverImages = mock(ChallengeCoverImages.class);
	private final InviteCodeAttemptLimiter attemptLimiter = mock(InviteCodeAttemptLimiter.class);
	private final ReadChallengeService service = new ReadChallengeService(challenges, participations, coverImages, attemptLimiter);

	@Test
	void returnsNextCursorFromLastReturnedRowWhenMoreResultsExist() {
		when(challenges.findRecruitingPublic(ChallengeCategory.READING, "책", 40L, 3)).thenReturn(List.of(
				new ChallengeSummary(39, "독서 A", "함께 읽어요", ChallengeCategory.READING, LocalDate.now(), LocalDate.now().plusDays(6), 3, 1, 1000, null),
				new ChallengeSummary(38, "독서 B", null, ChallengeCategory.READING, LocalDate.now(), LocalDate.now().plusDays(6), 3, 1, 0, null),
				new ChallengeSummary(37, "독서 C", null, ChallengeCategory.READING, LocalDate.now(), LocalDate.now().plusDays(6), 3, 1, 0, null)));

		var page = service.list(new ListChallengesQuery(ChallengeCategory.READING, "책", 40L, 2));

		assertEquals(2, page.items().size());
		assertEquals("함께 읽어요", page.items().getFirst().description());
		assertEquals(1000, page.items().getFirst().pointAmount());
		assertEquals(38L, page.nextCursor());
		assertTrue(page.hasNext());
	}

	@Test
	void rejectsOversizedPageBeforeQuerying() {
		BusinessException error = assertThrows(BusinessException.class,
				() -> service.list(new ListChallengesQuery(null, null, null, 101)));
		assertEquals(CommonErrorCode.INVALID_REQUEST, error.getErrorCode());
		verifyNoInteractions(challenges);
	}

	@Test
	void allowsAnonymousToReadPublicChallengeButCannotApply() {
		when(challenges.findById(10L)).thenReturn(Optional.of(challenge(ChallengeVisibility.PUBLIC, 10L)));

		var result = service.get(10L, null);

		assertFalse(result.canApply());
		assertEquals(null, result.myParticipationStatus());
	}

	@Test
	void allowsPrivateChallengeForApplicant() {
		when(challenges.findById(10L)).thenReturn(Optional.of(challenge(ChallengeVisibility.PRIVATE, 20L)));
		when(participations.findStatus(10L, 30L)).thenReturn(Optional.of("PENDING"));
		when(coverImages.urls(List.of("challenges/test"))).thenReturn(Map.of("challenges/test", "https://storage.test/signed"));

		var result = service.get(10L, 30L);

		assertEquals("PENDING", result.myParticipationStatus());
		assertEquals("함께 읽어요", result.description());
		assertEquals("https://storage.test/signed", result.coverImageUrl());
		assertFalse(result.canApply());
	}

	@Test
	void rejectsPrivateChallengeForNonApplicant() {
		when(challenges.findById(10L)).thenReturn(Optional.of(challenge(ChallengeVisibility.PRIVATE, 20L)));
		when(participations.findStatus(10L, 30L)).thenReturn(Optional.empty());

		BusinessException error = assertThrows(BusinessException.class, () -> service.get(10L, 30L));

		assertEquals(ChallengeErrorCode.PRIVATE_CHALLENGE_FORBIDDEN, error.getErrorCode());
		verifyNoInteractions(coverImages);
	}

	@Test
	void allowsPrivateChallengeByInviteCodeForNonApplicantWithoutJoining() {
		when(challenges.findByInviteCode("123456")).thenReturn(Optional.of(challenge(ChallengeVisibility.PRIVATE, 20L)));
		when(participations.findStatus(10L, 30L)).thenReturn(Optional.empty());

		var result = service.getByInviteCode("123456", 30L);

		assertEquals(10L, result.id());
		assertEquals(null, result.myParticipationStatus());
		assertTrue(result.canApply());
	}

	@Test
	void showsMyStatusWhenEnteringByInviteCode() {
		when(challenges.findByInviteCode("123456")).thenReturn(Optional.of(challenge(ChallengeVisibility.PUBLIC, 20L)));
		when(participations.findStatus(10L, 30L)).thenReturn(Optional.of("PENDING"));

		var result = service.getByInviteCode("123456", 30L);

		assertEquals("PENDING", result.myParticipationStatus());
		assertFalse(result.canApply());
	}

	@Test
	void rejectsUnknownInviteCode() {
		when(challenges.findByInviteCode("000000")).thenReturn(Optional.empty());

		BusinessException error = assertThrows(BusinessException.class, () -> service.getByInviteCode("000000", 30L));

		assertEquals(ChallengeErrorCode.INVALID_INVITE_CODE, error.getErrorCode());
		assertEquals("유효하지 않은 코드입니다", error.getErrorCode().message());
		verify(attemptLimiter).recordFailure(30L);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "12345", "1234567"})
	void rejectsInviteCodeWithWrongLengthWithoutQuerying(String code) {
		BusinessException error = assertThrows(BusinessException.class, () -> service.getByInviteCode(code, 30L));

		assertEquals(ChallengeErrorCode.INVALID_INVITE_CODE, error.getErrorCode());
		verify(attemptLimiter).recordFailure(30L);
		verifyNoInteractions(challenges);
	}

	@Test
	void blocksEvenValidInviteCodeWhenAttemptsExceeded() {
		when(attemptLimiter.isBlocked(30L)).thenReturn(true);

		BusinessException error = assertThrows(BusinessException.class, () -> service.getByInviteCode("123456", 30L));

		assertEquals(ChallengeErrorCode.INVITE_CODE_ATTEMPTS_EXCEEDED, error.getErrorCode());
		assertEquals(429, error.getErrorCode().status().value());
		verifyNoInteractions(challenges);
		verify(attemptLimiter, never()).recordFailure(30L);
	}

	@Test
	void doesNotCountValidOrEndedInviteCodeAsFailure() {
		when(challenges.findByInviteCode("123456")).thenReturn(Optional.of(challenge(ChallengeVisibility.PUBLIC, 20L)));
		when(challenges.findByInviteCode("654321"))
				.thenReturn(Optional.of(challenge(ChallengeVisibility.PUBLIC, 20L, ChallengeStatus.ENDED)));

		service.getByInviteCode("123456", 30L);
		assertThrows(BusinessException.class, () -> service.getByInviteCode("654321", 30L));

		verify(attemptLimiter, never()).recordFailure(30L);
	}

	@ParameterizedTest
	@EnumSource(value = ChallengeStatus.class, names = {"FINALIZING", "ENDED", "DISCARDED"})
	void rejectsEndedChallengeByInviteCode(ChallengeStatus status) {
		when(challenges.findByInviteCode("123456")).thenReturn(Optional.of(challenge(ChallengeVisibility.PRIVATE, 20L, status)));

		BusinessException error = assertThrows(BusinessException.class, () -> service.getByInviteCode("123456", 30L));

		assertEquals(ChallengeErrorCode.CHALLENGE_ALREADY_ENDED, error.getErrorCode());
		assertEquals("이미 종료된 챌린지입니다", error.getErrorCode().message());
		verifyNoInteractions(participations, coverImages);
	}

	@ParameterizedTest
	@EnumSource(value = ChallengeStatus.class, names = {"START_HELD", "IN_PROGRESS"})
	void allowsNotEndedChallengeByInviteCode(ChallengeStatus status) {
		when(challenges.findByInviteCode("123456")).thenReturn(Optional.of(challenge(ChallengeVisibility.PUBLIC, 20L, status)));

		var result = service.getByInviteCode("123456", 30L);

		assertEquals(status.name(), result.status());
		assertEquals(status == ChallengeStatus.START_HELD, result.canApply());
	}

	private ChallengeDetail challenge(ChallengeVisibility visibility, long ownerId) {
		return challenge(visibility, ownerId, ChallengeStatus.RECRUITING);
	}

	private ChallengeDetail challenge(ChallengeVisibility visibility, long ownerId, ChallengeStatus status) {
		return new ChallengeDetail(10L, ownerId, "독서", "함께 읽어요", visibility, ChallengeCategory.READING, "목적", "매일 독서",
				"인증 규칙", null, status, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 7), 3, 1, 0, "challenges/test");
	}
}
