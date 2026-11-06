package com.morak.feature.participation.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.morak.common.exception.BusinessException;
import com.morak.feature.participation.domain.entity.Participation;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import java.sql.SQLException;
import java.time.LocalDateTime;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ParticipationRepositoryAdapterTest {

	@Test
	void translatesOnlyTheChallengeUserUniqueViolation() {
		JpaParticipationRepository repository = mock(JpaParticipationRepository.class);
		ParticipationRepositoryAdapter adapter = new ParticipationRepositoryAdapter(repository);
		Participation participation = Participation.apply(1L, 2L, null, LocalDateTime.of(2026, 11, 6, 10, 0));
		for (String constraint : new String[] {"uk_participation_challenge_user", "other_unique"}) {
			for (String sqlState : new String[] {"23505", "23503"}) {
				ConstraintViolationException cause = new ConstraintViolationException(
						"constraint failed", new SQLException("failure", sqlState), constraint);
				DataIntegrityViolationException failure = new DataIntegrityViolationException("save failed", cause);
				doThrow(failure).when(repository).saveAndFlush(participation);
				if (constraint.equals("uk_participation_challenge_user") && sqlState.equals("23505")) {
					BusinessException exception = assertThrows(BusinessException.class, () -> adapter.save(participation));
					assertEquals(ParticipationErrorCode.ALREADY_APPLIED, exception.getErrorCode());
				} else {
					assertSame(failure, assertThrows(DataIntegrityViolationException.class,
							() -> adapter.save(participation)));
				}
			}
		}
	}
}
