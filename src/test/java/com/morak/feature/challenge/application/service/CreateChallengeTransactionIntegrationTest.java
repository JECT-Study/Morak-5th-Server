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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.morak.common.exception.BusinessException;
import com.morak.feature.auth.application.port.out.ServiceTokenIssuer;
import com.morak.feature.auth.domain.vo.AuthUserId;
import com.morak.feature.challenge.application.dto.command.CreateChallengeCommand;
import com.morak.feature.challenge.application.port.out.ChallengeCoverStorage;
import com.morak.feature.challenge.application.port.out.ChallengeParticipation;
import com.morak.feature.challenge.application.port.out.ChallengeParticipation.InvalidatedApplication;
import com.morak.feature.challenge.application.port.out.InvalidatedApplicationNotice;
import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import com.morak.feature.challenge.domain.enums.ChallengeVisibility;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import com.morak.feature.challenge.domain.exception.InviteCodeConflictException;
import com.morak.feature.point.application.service.PointService;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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
	@Autowired private PointService pointService;
	@MockitoBean private ChallengeParticipation participants;
	@MockitoBean private InvalidatedApplicationNotice notices;
	@MockitoBean private ChallengeCoverStorage coverStorage;

	@BeforeEach
	void clearFixtures() {
		jdbc.sql("""
				TRUNCATE morak.challenge, morak.participation, morak.chat_message, morak.point_transaction, morak.point_account CASCADE
				""").update();
		jdbc.sql("""
				INSERT INTO morak.users (id, status, created_at, updated_at)
				VALUES (1, 'ACTIVE', now(), now()), (2, 'ACTIVE', now(), now())
				ON CONFLICT (id) DO NOTHING
				""").update();
		// 등록 포트가 개설 트랜잭션을 공유하는지 확인하기 위한 테스트용 기록이다.
		doAnswer(invocation -> {
			jdbc.sql("""
					INSERT INTO morak.participation (challenge_id, user_id, role, status, applied_at, joined_at, deposit_amount, point_status)
					VALUES (:challengeId, :userId, 'HOST', 'ACTIVE', now(), now(), :amount, :pointStatus)
					""")
					.param("challengeId", invocation.<Long>getArgument(0))
					.param("userId", invocation.<Long>getArgument(1))
					.param("amount", invocation.<Integer>getArgument(3))
					.param("pointStatus", invocation.<Integer>getArgument(3) > 0 ? "HELD" : "NONE")
					.update();
			return 100L;
		}).when(participants).registerHost(anyLong(), anyLong(), any(Instant.class), org.mockito.ArgumentMatchers.anyInt());
	}

	@Test
	void createsChallengeThroughApiWithHostRegistrationStubbed() throws Exception {
		jdbc.sql("UPDATE morak.users SET onboarded_at = now() WHERE id = 1").update();
		doAnswer(invocation -> {
			jdbc.sql("INSERT INTO morak.participation (challenge_id, user_id, role, status, applied_at, joined_at, deposit_amount, point_status) VALUES (:challengeId, 1, 'HOST', 'ACTIVE', now(), now(), :amount, :pointStatus)")
					.param("challengeId", invocation.<Long>getArgument(0))
					.param("amount", invocation.<Integer>getArgument(3))
					.param("pointStatus", invocation.<Integer>getArgument(3) > 0 ? "HELD" : "NONE").update();
			return 100L;
		}).when(participants).registerHost(anyLong(), eq(1L), any(Instant.class), org.mockito.ArgumentMatchers.anyInt());
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
		verify(participants).registerHost(eq(id), eq(1L), any(Instant.class), eq(0));
		assertEquals(1L, number("SELECT count(*) FROM morak.participation"));

		mvc.perform(post("/challenges")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody.replace("\"description\": \"매일 함께 책 읽어요\",", "")))
				.andExpect(status().isBadRequest());
		assertEquals(1L, number("SELECT count(*) FROM morak.challenge"));
	}

	@Test
	void uploadsCoverAndSavesItsKey() throws Exception {
		byte[] photo = png();
		createWithCover(photo, 0).andExpect(status().isCreated());

		String key = jdbc.sql("SELECT cover_image_key FROM morak.challenge").query(String.class).single();
		verify(coverStorage).upload(eq(key), eq(photo), eq("image/png"));
	}

	@Test
	void deletesUploadedCoverWhenCreationFails() throws Exception {
		pointService.grant(1L, com.morak.feature.point.domain.entity.PointGrantReason.SIGNUP_GIFT, 500, "test:cover:1");
		createWithCover(png(), 1000).andExpect(status().isBadRequest());

		var key = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(coverStorage).upload(key.capture(), any(byte[].class), eq("image/png"));
		verify(coverStorage).delete(key.getValue());
		assertEquals(0L, number("SELECT count(*) FROM morak.challenge"));
	}

	@Test
	void rejectsOversizedAndUnsupportedCoversBeforeUploading() throws Exception {
		createWithCover(new byte[ChallengeCoverImages.MAX_IMAGE_BYTES + 1], 0).andExpect(status().is(413));
		createWithCover(new byte[] {1, 2, 3}, 0).andExpect(status().isBadRequest());
		verifyNoInteractions(coverStorage);
	}

	@Test
	void uploadsExtendedWebpWithAnUnknownChunk() throws Exception {
		byte[] photo = extendedWebp(1);

		createWithCover(photo, 0).andExpect(status().isCreated());

		verify(coverStorage).upload(any(String.class), eq(photo), eq("image/webp"));
	}

	@Test
	void rejectsOversizedWebpFrameBeforeUploading() throws Exception {
		createWithCover(extendedWebp(6_000), 0)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("CHALLENGE_INVALID_COVER_IMAGE"));

		verifyNoInteractions(coverStorage);
	}

	private static byte[] extendedWebp(int frameDimension) {
		byte[] simple = Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
		// Wrap the 1×1 image in a VP8X canvas and append an unknown, empty chunk.
		ByteBuffer data = ByteBuffer.allocate(simple.length + 18 + 8).order(ByteOrder.LITTLE_ENDIAN);
		data.put(simple, 0, 12);
		data.putInt(4, data.capacity() - 8);
		data.put("VP8X".getBytes(StandardCharsets.US_ASCII)).putInt(10).put(new byte[10]);
		int frameChunk = data.position();
		data.put(simple, 12, simple.length - 12);
		data.putShort(frameChunk + 8 + 6, (short) frameDimension);
		data.putShort(frameChunk + 8 + 8, (short) frameDimension);
		data.put("JUNK".getBytes(StandardCharsets.US_ASCII)).putInt(0);
		return data.array();
	}

	private ResultActions createWithCover(byte[] photo, int pointAmount) throws Exception {
		jdbc.sql("UPDATE morak.users SET onboarded_at = now() WHERE id = 1").update();
		String token = tokens.createAccessToken(new AuthUserId(1L), true).value();
		String request = """
				{
				"title": "독서",
				"visibility": "PUBLIC",
				"category": "READING",
				"description": "함께 책 읽어요",
				"commonMission": "미션",
				"verificationRules": "인증",
				"startDate": "2026-12-01",
				"endDate": "2026-12-07",
				"capacity": 3,
				"pointAmount": %d
				}
				""".formatted(pointAmount);
		return mvc.perform(multipart("/challenges")
				.file(new MockMultipartFile("challenge", "challenge.json", MediaType.APPLICATION_JSON_VALUE,
						request.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
				.file(new MockMultipartFile("coverImage", "cover.png", "image/png", photo))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private byte[] png() {
		return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jD40AAAAASUVORK5CYII=");
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
	void translatesDatabaseInviteCodeCollisionAndRollsBackTheSecondCreation() {
		transaction.create(1, command(0), "000001");

		assertThrows(InviteCodeConflictException.class, () -> transaction.create(2, command(0), "000001"));

		assertEquals(1L, number("SELECT count(*) FROM morak.challenge"));
		assertEquals(1L, number("SELECT count(*) FROM morak.participation"));
	}

	@Test
	void depositsHostPointsAndPersistsChallengeAmount() {
		pointService.grant(1L, com.morak.feature.point.domain.entity.PointGrantReason.SIGNUP_GIFT, 5000, "test:signup:1");
		long id = transaction.create(1, command(1000), "000001");

		assertEquals(1000L, jdbc.sql("SELECT point_amount FROM morak.challenge WHERE id = :id")
				.param("id", id).query(Long.class).single());
		assertEquals(4000L, jdbc.sql("SELECT balance FROM morak.point_account WHERE user_id = 1").query(Long.class).single());
		assertEquals("DEPOSIT:-1000", jdbc.sql("SELECT type || ':' || amount FROM morak.point_transaction WHERE idempotency_key = 'DEPOSIT:100'")
				.query(String.class).single());
		assertEquals(1L, number("SELECT count(*) FROM morak.point_transaction WHERE idempotency_key = 'DEPOSIT:100'"));
		assertEquals("HELD:1000", jdbc.sql("SELECT point_status || ':' || deposit_amount FROM morak.participation WHERE challenge_id = :id AND user_id = 1")
				.param("id", id)
				.query(String.class).single());
	}

	@Test
	void rollsBackChallengeHostAndPointChangeWhenBalanceIsInsufficient() {
		pointService.grant(1L, com.morak.feature.point.domain.entity.PointGrantReason.SIGNUP_GIFT, 500, "test:signup:2");

		assertThrows(BusinessException.class, () -> transaction.create(1, command(1000), "000001"));

		assertEquals(0L, number("SELECT count(*) FROM morak.challenge"));
		assertEquals(0L, number("SELECT count(*) FROM morak.participation"));
		assertEquals(500L, jdbc.sql("SELECT balance FROM morak.point_account WHERE user_id = 1").query(Long.class).single());
		assertEquals(1L, number("SELECT count(*) FROM morak.point_transaction"));
	}

	@Test
	void rollsBackPointDepositWhenAnOuterTransactionRollsBack() {
		pointService.grant(1L, com.morak.feature.point.domain.entity.PointGrantReason.SIGNUP_GIFT, 5000, "test:signup:3");
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			transaction.create(1, command(1000), "000001");
			status.setRollbackOnly();
		});

		assertEquals(0L, number("SELECT count(*) FROM morak.challenge"));
		assertEquals(0L, number("SELECT count(*) FROM morak.participation"));
		assertEquals(5000L, jdbc.sql("SELECT balance FROM morak.point_account WHERE user_id = 1").query(Long.class).single());
		assertEquals(1L, number("SELECT count(*) FROM morak.point_transaction"));
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
	@Disabled("HOTFIX: 동시 참여 5개 제한 임시 해제. 제한을 복구할 때 함께 활성화한다")
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
	@Disabled("HOTFIX: 동시 참여 5개 제한 임시 해제. 제한을 복구할 때 함께 활성화한다")
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
