package com.morak.feature.challenge.application.service;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.morak.common.exception.BusinessException;
import com.morak.feature.auth.application.port.out.ServiceTokenIssuer;
import com.morak.feature.auth.domain.vo.AuthUserId;
import com.morak.feature.challenge.application.dto.CreateChallengeCommand;
import com.morak.feature.challenge.application.port.out.ChallengeParticipation;
import com.morak.feature.challenge.application.port.out.ChallengeParticipation.InvalidatedApplication;
import com.morak.feature.challenge.application.port.out.InvalidatedApplicationNotice;
import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import com.morak.feature.challenge.domain.enums.ChallengeVisibility;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
		"morak.auth.jwt.secret=integration-test-secret-integration-test-secret",
		"morak.auth.refresh-token-cleanup.cron=-",
		"morak.auth.refresh-token-cleanup.retention=P7D"
})
@AutoConfigureMockMvc
class CreateChallengeTransactionIntegrationTest {

	@TestConfiguration(proxyBeanMethods = false)
	static class Containers {
		@Bean
		@ServiceConnection
		PostgreSQLContainer postgres() {
			return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
		}

		@Bean
		@Primary
		Clock challengeTestClock() {
			return Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC);
		}
	}

	@Autowired private JdbcClient jdbc;
	@Autowired private MockMvc mvc;
	@Autowired private ServiceTokenIssuer tokens;
	@Autowired private CreateChallengeTransaction transaction;
	@Autowired private PlatformTransactionManager transactionManager;
	@MockitoBean private ChallengeParticipation participants;
	@MockitoBean private InvalidatedApplicationNotice notices;

	@BeforeEach
	void clearFixtures() {
		jdbc.sql("""
				TRUNCATE morak.challenge, morak.participation, morak.chat_message
				""").update();
		jdbc.sql("""
				INSERT INTO morak.users (id, status, created_at, updated_at)
				VALUES (1, 'ACTIVE', now(), now()), (2, 'ACTIVE', now(), now())
				ON CONFLICT (id) DO NOTHING
				""").update();
		// 등록 포트가 개설 트랜잭션을 공유하는지 확인하기 위한 테스트용 기록이다.
		doAnswer(invocation -> {
			jdbc.sql("""
					INSERT INTO morak.participation (challenge_id, user_id, role, status, applied_at, joined_at)
					VALUES (:challengeId, :userId, 'HOST', 'ACTIVE', now(), now())
					""")
					.param("challengeId", invocation.<Long>getArgument(0))
					.param("userId", invocation.<Long>getArgument(1))
					.update();
			return null;
		}).when(participants).registerHost(anyLong(), anyLong(), any(Instant.class));
	}

	@Test
	void createsChallengeThroughApiWithHostRegistrationStubbed() throws Exception {
		jdbc.sql("UPDATE morak.users SET onboarded_at = now() WHERE id = 1").update();
		doNothing().when(participants).registerHost(anyLong(), eq(1L), any(Instant.class));
		String token = tokens.createAccessToken(new AuthUserId(1L), true).value();

		String requestBody = """
						{
						"title": "독서",
						"visibility": "PUBLIC",
						"category": "READING",
						"description": "매일 함께 책 읽어요",
						"commonMission": "미션",
						"verificationRules": "인증",
						"startDate": "2026-12-01",
						"endDate": "2026-12-14",
						"capacity": 3,
						"pointAmount": 0
						}
						""";
		var response = mvc.perform(post("/challenges")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isCreated());

		long id = number("SELECT id FROM morak.challenge WHERE owner_user_id = 1 AND status = 'RECRUITING'");
		response.andExpect(jsonPath("$.data.challengeId").value(is(id), Long.class));
		assertEquals("매일 함께 책 읽어요", jdbc.sql("""
				SELECT description FROM morak.challenge
				WHERE id = :id AND purpose IS NULL AND duration_weeks IS NULL AND end_date - start_date + 1 = 14
				""").param("id", id).query(String.class).single());
		verify(participants).registerHost(eq(id), eq(1L), any(Instant.class));
		assertEquals(0L, number("SELECT count(*) FROM morak.participation"));

		mvc.perform(post("/challenges")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody.replace("\"description\": \"매일 함께 책 읽어요\",", "")))
				.andExpect(status().isBadRequest());
		assertEquals(1L, number("SELECT count(*) FROM morak.challenge"));
	}

	@Test
	void createsRecruitingChallengeAndRegistersActiveHost() {
		long id = transaction.create(1, command(0), "000001");

		assertEquals("RECRUITING:1", jdbc.sql("""
				SELECT status || ':' || current_count FROM morak.challenge WHERE id = :id
				""").param("id", id).query(String.class).single());
		assertEquals("HOST:ACTIVE", jdbc.sql("""
				SELECT role || ':' || status FROM morak.participation
				""").query(String.class).single());
		assertEquals(0L, number("SELECT count(*) FROM morak.point_account"));
	}

	@Test
	void rejectsCreationThatWouldRequireChargingPoints() {
		BusinessException error = assertThrows(BusinessException.class,
				() -> transaction.create(1, command(1000), "000001"));

		assertEquals(ChallengeErrorCode.POINTS_NOT_SUPPORTED, error.getErrorCode());
		assertEquals(0L, number("SELECT count(*) FROM morak.challenge"));
		assertEquals(0L, number("SELECT count(*) FROM morak.participation"));
	}

	@Test
	void rollsBackChallengeAndHostTogether() {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			transaction.create(1, command(0), "000001");
			status.setRollbackOnly();
		});

		assertEquals(0L, number("SELECT count(*) FROM morak.challenge"));
		assertEquals(0L, number("SELECT count(*) FROM morak.participation"));
	}

	@Test
	void onlyOneConcurrentCreationReachesTheLimitAndInvalidatesPendingApplication() throws Exception {
		long otherChallenge = createWithFourActiveChallenges();
		when(participants.invalidatePendingWithoutPoints(1L))
				.thenReturn(Optional.of(List.of(new InvalidatedApplication(42L, otherChallenge, 2L))));

		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(creation("100005", start));
			var second = executor.submit(creation("100006", start));
			start.countDown();
			assertEquals(1, (first.get(10, TimeUnit.SECONDS) ? 1 : 0) + (second.get(10, TimeUnit.SECONDS) ? 1 : 0));
		}

		assertEquals(5L, number("SELECT count(*) FROM morak.participation WHERE user_id = 1 AND status = 'ACTIVE'"));
		verify(participants).invalidatePendingWithoutPoints(1L);
		verify(notices).send(eq(2L), eq(otherChallenge), eq(42L), any(Instant.class));
	}

	@Test
	void rejectsFifthCreationWhenParticipationPortRequiresPointHandling() {
		createWithFourActiveChallenges();
		when(participants.invalidatePendingWithoutPoints(1L)).thenReturn(Optional.empty());

		BusinessException error = assertThrows(BusinessException.class,
				() -> transaction.create(1, command(0), "100005"));

		assertEquals(ChallengeErrorCode.PENDING_POINTS_NOT_SUPPORTED, error.getErrorCode());
		assertEquals(3L, number("SELECT count(*) FROM morak.challenge WHERE owner_user_id = 1"));
		verifyNoInteractions(notices);
	}

	/** 개설한 세 방과 일반 참가자로 합류한 한 방을 함께 참여 한도에 포함시킨다. */
	private long createWithFourActiveChallenges() {
		long otherChallenge = transaction.create(2, command(0), "100000");
		long joinedChallenge = transaction.create(2, command(0), "100004");
		for (int index = 1; index <= 3; index++) {
			transaction.create(1, command(0), "10000" + index);
		}
		jdbc.sql("""
				INSERT INTO morak.participation (challenge_id, user_id, role, status, applied_at, joined_at)
				VALUES (:challengeId, 1, 'MEMBER', 'ACTIVE', now(), now())
				""").param("challengeId", joinedChallenge).update();
		jdbc.sql("UPDATE morak.challenge SET current_count = 2 WHERE id = :challengeId")
				.param("challengeId", joinedChallenge).update();
		return otherChallenge;
	}

	private Callable<Boolean> creation(String code, CountDownLatch start) {
		return () -> {
			if (!start.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("개설 요청 시작 대기 시간 초과");
			}
			try {
				transaction.create(1, command(0), code);
				return true;
			} catch (BusinessException exception) {
				assertEquals(ChallengeErrorCode.PARTICIPATION_LIMIT, exception.getErrorCode());
				return false;
			}
		};
	}

	private long number(String sql) {
		return jdbc.sql(sql).query(Long.class).single();
	}

	private CreateChallengeCommand command(int amount) {
		LocalDate start = LocalDate.of(2026, 12, 1);
		return new CreateChallengeCommand("독서", ChallengeVisibility.PUBLIC, ChallengeCategory.READING, "한줄 소개", "목적", "미션", "인증",
				start, start.plusDays(6), 3, amount, null);
	}
}
