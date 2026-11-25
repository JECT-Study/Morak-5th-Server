package com.morak.feature.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.morak.common.exception.BusinessException;
import com.morak.feature.user.application.dto.result.MyUserInfo;
import com.morak.feature.user.application.service.UserService;
import com.morak.feature.user.domain.exception.UserErrorCode;
import com.morak.feature.user.domain.vo.OnboardingProfile;
import com.morak.feature.user.domain.vo.UserId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

/** 실제 PostgreSQL로 온보딩 완료 시 가입 포인트 1회 지급(위키 §4.1, §4.6)과 프로필 수정을 검증한다. Docker가 필요하다. */
@SpringBootTest(properties = "morak.auth.jwt.secret=integration-test-secret-integration-test-secret")
class OnboardingIntegrationTest {

	private static final long USER_ID = 1L;
	private static final long OTHER_USER_ID = 2L;
	private static final int THREADS = 5;

	@TestConfiguration(proxyBeanMethods = false)
	static class Containers {
		@Bean
		@ServiceConnection
		PostgreSQLContainer postgres() {
			return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
		}
	}

	@Autowired private JdbcClient jdbc;
	@Autowired private UserService userService;

	@BeforeEach
	void setUp() {
		jdbc.sql("TRUNCATE morak.users, morak.user_interest, morak.point_transaction, morak.point_account CASCADE")
				.update();
		insertUser(USER_ID, null, false);
		insertUser(OTHER_USER_ID, "Taken", true);
	}

	@Test
	void grantsSignupGiftOnceEvenWhenCompletedAgain() {
		userService.completeOnboarding(new UserId(USER_ID), profile("Morak"));

		BusinessException error = assertThrows(BusinessException.class,
				() -> userService.completeOnboarding(new UserId(USER_ID), profile("Morak")));

		assertEquals(UserErrorCode.ALREADY_ONBOARDED, error.getErrorCode());
		assertEquals(10_000L, balance(USER_ID));
		var row = jdbc.sql("""
				SELECT t.type, t.reason, t.amount FROM morak.point_transaction t
				JOIN morak.point_account a ON a.id = t.account_id WHERE a.user_id = :id
				""").param("id", USER_ID).query().singleRow();
		assertEquals("GRANT", row.get("type"));
		assertEquals("SIGNUP_GIFT", row.get("reason"));
		assertEquals(10_000L, ((Number) row.get("amount")).longValue());
		assertEquals(1L, count("SELECT count(*) FROM morak.point_transaction WHERE idempotency_key = 'SIGNUP:1'"));
	}

	@Test
	void concurrentCompletionsGrantOnce() throws Exception {
		List<Callable<Object>> tasks = new ArrayList<>();
		for (int i = 0; i < THREADS; i++) {
			tasks.add(() -> userService.completeOnboarding(new UserId(USER_ID), profile("Morak")));
		}

		List<Object> outcomes = runAll(tasks);

		long rejected = outcomes.stream()
				.filter(outcome -> outcome instanceof BusinessException exception
						&& exception.getErrorCode() == UserErrorCode.ALREADY_ONBOARDED)
				.count();
		assertEquals(THREADS - 1, rejected);
		assertEquals(10_000L, balance(USER_ID));
		assertEquals(1L, count("SELECT count(*) FROM morak.point_transaction"));
	}

	@Test
	void duplicateNicknameRollsBackOnboardingAndGrant() {
		BusinessException error = assertThrows(BusinessException.class,
				() -> userService.completeOnboarding(new UserId(USER_ID), profile("Taken")));

		assertEquals(UserErrorCode.NICKNAME_ALREADY_IN_USE, error.getErrorCode());
		assertEquals(0L, count("SELECT count(*) FROM morak.users WHERE onboarded_at IS NOT NULL AND id = 1"));
		assertEquals(0L, count("SELECT count(*) FROM morak.point_transaction"));
	}

	@Test
	void profileUpdateRejectsTakenNicknameAndAllowsOwnNickname() {
		userService.completeOnboarding(new UserId(USER_ID), profile("Morak"));

		BusinessException error = assertThrows(BusinessException.class,
				() -> userService.updateProfile(new UserId(USER_ID), profile("Taken")));
		assertEquals(UserErrorCode.NICKNAME_ALREADY_IN_USE, error.getErrorCode());

		MyUserInfo info = userService.updateProfile(new UserId(USER_ID),
				OnboardingProfile.from("Morak", "새 소개", null, List.of("READING")));
		assertEquals(List.of("READING"), info.interests());
		assertEquals(List.of("READING"), userService.getMyInfo(new UserId(USER_ID)).interests());
		var row = jdbc.sql("SELECT nickname, bio FROM morak.users WHERE id = :id").param("id", USER_ID).query().singleRow();
		assertEquals("Morak", row.get("nickname"));
		assertEquals("새 소개", row.get("bio"));
		assertEquals(List.of("READING"), jdbc.sql("SELECT interest_code FROM morak.user_interest WHERE user_id = :id")
				.param("id", USER_ID).query(String.class).list());
		assertEquals(10_000L, balance(USER_ID));
	}

	private static OnboardingProfile profile(String nickname) {
		return OnboardingProfile.from(nickname, null, null, List.of("STUDY"));
	}

	private void insertUser(long id, String nickname, boolean onboarded) {
		jdbc.sql("""
				INSERT INTO morak.users (id, email, nickname, status, terms_agreed_at, marketing_agreed, onboarded_at,
										created_at, updated_at)
				VALUES (:id, 'a@example.com', :nickname, 'ACTIVE', now(), false,
						CASE WHEN :onboarded THEN now() END, now(), now())
				""").param("id", id).param("nickname", nickname).param("onboarded", onboarded).update();
	}

	private long balance(long userId) {
		return jdbc.sql("SELECT balance FROM morak.point_account WHERE user_id = :id").param("id", userId)
				.query(Long.class).single();
	}

	private long count(String sql) {
		return jdbc.sql(sql).query(Long.class).single();
	}

	private static List<Object> runAll(List<Callable<Object>> tasks) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		try {
			List<Object> outcomes = new ArrayList<>();
			for (Future<Object> future : executor.invokeAll(tasks)) {
				try {
					outcomes.add(future.get());
				} catch (java.util.concurrent.ExecutionException exception) {
					outcomes.add(exception.getCause());
				}
			}
			return outcomes;
		} finally {
			executor.shutdownNow();
		}
	}
}
