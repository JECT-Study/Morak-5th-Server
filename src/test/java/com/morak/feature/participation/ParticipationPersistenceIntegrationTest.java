package com.morak.feature.participation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.common.exception.BusinessException;
import com.morak.feature.participation.application.query.ParticipationQuery;
import com.morak.feature.participation.domain.entity.Participation;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import com.morak.feature.participation.domain.repository.ParticipationRepository;
import com.morak.feature.participation.domain.vo.DepositStatus;
import com.morak.feature.participation.domain.vo.ParticipationEndReason;
import com.morak.feature.participation.domain.vo.ParticipationRole;
import com.morak.feature.participation.domain.vo.ParticipationStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 PostgreSQL(Testcontainers)로 참가자 엔티티 매핑과 유니크 제약을 확인한다. Docker가 실행 중이어야 한다.
 * 마이그레이션이 적용되고 엔티티 매핑 검증(ddl-auto=validate)을 통과하는지도 함께 확인된다.
 */
@SpringBootTest(properties = "morak.auth.jwt.secret=integration-test-secret-integration-test-secret")
class ParticipationPersistenceIntegrationTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 10, 0);
	private static final long CHALLENGE = 100L;

	@TestConfiguration(proxyBeanMethods = false)
	static class Containers {

		@Bean
		@ServiceConnection
		PostgreSQLContainer postgres() {
			return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
		}
	}

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private ParticipationRepository repository;

	@Autowired
	private ParticipationQuery query;

	@BeforeEach
	void setUp() {
		jdbc.sql("DELETE FROM morak.chat_message").update();
		jdbc.sql("DELETE FROM morak.participation").update();
	}

	@Test
	void applicationLifecycleIsPersistedColumnByColumn() {
		Long id = repository.save(Participation.apply(CHALLENGE, 2L, "신청합니다", NOW)).getId();
		assertNotNull(id);

		Participation approved = repository.findById(id).orElseThrow();
		approved.approve(1L, 5000, NOW.plusDays(1), NOW.plusHours(1));
		repository.save(approved);
		Participation active = repository.findById(id).orElseThrow();
		assertEquals(ParticipationStatus.APPROVED, active.getStatus());
		assertEquals(5000, active.getDepositAmount());
		assertEquals(NOW.plusDays(1), active.getPaymentDueAt());

		active.confirmPayment(NOW.plusHours(2));
		repository.save(active);
		Participation joined = repository.findById(id).orElseThrow();
		assertEquals(ParticipationStatus.ACTIVE, joined.getStatus());
		assertEquals(DepositStatus.PAID, joined.getDepositStatus());
		assertEquals(NOW.plusHours(2), joined.getJoinedAt());

		joined.expel(ParticipationEndReason.HOST_EXPEL, NOW.plusDays(2));
		repository.save(joined);
		Participation expelled = repository.findByChallengeIdAndUserId(CHALLENGE, 2L).orElseThrow();
		assertEquals(ParticipationStatus.EXPELLED, expelled.getStatus());
		assertEquals(ParticipationEndReason.HOST_EXPEL, expelled.getEndReason());
		assertEquals(ParticipationRole.MEMBER, expelled.getRole());
		assertEquals(0, expelled.getMissedCount());
	}

	@Test
	void secondApplicationForSameChallengeAndUserIsRejected() {
		repository.save(Participation.apply(CHALLENGE, 2L, null, NOW));

		BusinessException exception = assertThrows(BusinessException.class,
				() -> repository.save(Participation.apply(CHALLENGE, 2L, null, NOW)));

		assertEquals(ParticipationErrorCode.ALREADY_APPLIED, exception.getErrorCode());
		repository.save(Participation.apply(CHALLENGE + 1, 2L, null, NOW));
		repository.save(Participation.apply(CHALLENGE, 3L, null, NOW));
	}

	@Test
	void reappliedRowIsReusedAfterCancel() {
		Participation participation = repository.save(Participation.apply(CHALLENGE, 2L, "처음", NOW));
		participation.cancel();
		repository.save(participation);

		Participation canceled = repository.findByChallengeIdAndUserId(CHALLENGE, 2L).orElseThrow();
		canceled.reapply("다시", NOW.plusDays(1));
		repository.save(canceled);

		Participation again = repository.findByChallengeIdAndUserId(CHALLENGE, 2L).orElseThrow();
		assertEquals(participation.getId(), again.getId());
		assertEquals(ParticipationStatus.PENDING, again.getStatus());
		assertEquals("다시", again.getApplyMessage());
		assertEquals(NOW.plusDays(1), again.getAppliedAt());
	}

	@Test
	void queryExposesOnlyJoinedParticipantsAndMapsRoleAndStatus() {
		Long hostId = repository.save(Participation.host(CHALLENGE, 1L, NOW)).getId();
		Participation noticed = repository.save(activeMember(2L));
		noticed.noticeExpulsion();
		repository.save(noticed);
		repository.save(Participation.apply(CHALLENGE, 3L, null, NOW));
		Participation completed = repository.save(activeMember(4L));
		completed.complete(NOW.plusDays(10));
		repository.save(completed);

		assertEquals("HOST", query.findJoinedParticipant(CHALLENGE, 1L).orElseThrow().role());
		assertEquals("EXPEL_NOTICED", query.findJoinedParticipant(CHALLENGE, 2L).orElseThrow().status());
		assertEquals("COMPLETED", query.findJoinedParticipant(CHALLENGE, 4L).orElseThrow().status());
		assertTrue(query.findJoinedParticipant(CHALLENGE, 3L).isEmpty());
		assertTrue(query.findJoinedParticipant(CHALLENGE, 99L).isEmpty());

		var byId = query.findParticipantsByIds(List.of(hostId, noticed.getId(), -1L));
		assertEquals(2, byId.size());
		assertEquals(1L, byId.get(hostId).userId());
		assertTrue(query.findParticipantsByIds(List.of()).isEmpty());
	}

	private Participation activeMember(long userId) {
		Participation participation = Participation.apply(CHALLENGE, userId, null, NOW);
		participation.approve(1L, 0, null, NOW);
		return participation;
	}
}
