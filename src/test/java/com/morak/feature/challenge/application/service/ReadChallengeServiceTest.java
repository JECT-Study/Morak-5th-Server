package com.morak.feature.challenge.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.challenge.application.port.out.ChallengeParticipationReader;
import com.morak.feature.challenge.application.query.ChallengeReadQuery;
import com.morak.feature.challenge.application.query.ChallengeReadQuery.ChallengeDetail;
import com.morak.feature.challenge.application.query.ChallengeReadQuery.ChallengeSummary;
import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReadChallengeServiceTest {

	private final ChallengeReadQuery challenges = mock(ChallengeReadQuery.class);
	private final ChallengeParticipationReader participations = mock(ChallengeParticipationReader.class);
	private final ChallengeCoverImages coverImages = mock(ChallengeCoverImages.class);
	private final ReadChallengeService service = new ReadChallengeService(challenges, participations, coverImages);

	@Test
	void returnsNextCursorFromLastReturnedRowWhenMoreResultsExist() {
		when(challenges.findRecruitingPublic(ChallengeCategory.READING, "책", 40L, 3)).thenReturn(List.of(
				new ChallengeSummary(39, "독서 A", "함께 읽어요", ChallengeCategory.READING, LocalDate.now(), LocalDate.now().plusDays(6), 3, 1, null),
				new ChallengeSummary(38, "독서 B", null, ChallengeCategory.READING, LocalDate.now(), LocalDate.now().plusDays(6), 3, 1, null),
				new ChallengeSummary(37, "독서 C", null, ChallengeCategory.READING, LocalDate.now(), LocalDate.now().plusDays(6), 3, 1, null)));

		var page = service.list(ChallengeCategory.READING, "책", 40L, 2);

		assertEquals(2, page.items().size());
		assertEquals("함께 읽어요", page.items().getFirst().description());
		assertEquals(38L, page.nextCursor());
		assertTrue(page.hasNext());
	}

	@Test
	void rejectsOversizedPageBeforeQuerying() {
		BusinessException error = assertThrows(BusinessException.class, () -> service.list(null, null, null, 101));
		assertEquals(CommonErrorCode.INVALID_REQUEST, error.getErrorCode());
		verifyNoInteractions(challenges);
	}

	@Test
	void allowsAnonymousToReadPublicChallengeButCannotApply() {
		when(challenges.findById(10L)).thenReturn(Optional.of(challenge("PUBLIC", 10L)));

		var result = service.get(10L, null);

		assertFalse(result.canApply());
		assertEquals(null, result.myParticipationStatus());
	}

	@Test
	void allowsPrivateChallengeForApplicant() {
		when(challenges.findById(10L)).thenReturn(Optional.of(challenge("PRIVATE", 20L)));
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
		when(challenges.findById(10L)).thenReturn(Optional.of(challenge("PRIVATE", 20L)));
		when(participations.findStatus(10L, 30L)).thenReturn(Optional.empty());

		BusinessException error = assertThrows(BusinessException.class, () -> service.get(10L, 30L));

		assertEquals(ChallengeErrorCode.PRIVATE_CHALLENGE_FORBIDDEN, error.getErrorCode());
		verifyNoInteractions(coverImages);
	}

	private ChallengeDetail challenge(String visibility, long ownerId) {
		return new ChallengeDetail(10L, ownerId, "독서", "함께 읽어요", visibility, ChallengeCategory.READING, "목적", "매일 독서",
				"인증 규칙", null, "RECRUITING", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 7), 3, 1, "challenges/test");
	}
}
