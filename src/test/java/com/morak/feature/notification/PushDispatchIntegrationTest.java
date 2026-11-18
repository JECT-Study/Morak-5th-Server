package com.morak.feature.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.morak.feature.notification.application.dto.NotificationContent.ParticipationApproved;
import com.morak.feature.notification.application.dto.NotifyCommand;
import com.morak.feature.notification.application.dto.PushMessage;
import com.morak.feature.notification.application.dto.PushReceipt;
import com.morak.feature.notification.application.dto.PushTicket;
import com.morak.feature.notification.application.port.out.PushGateway;
import com.morak.feature.notification.application.service.NotificationService;
import com.morak.feature.notification.application.service.PushTokenService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 PostgreSQL(Testcontainers)과 실제 트랜잭션으로, 알림 커밋 뒤에만 푸시가 비동기로 나가는지 확인한다.
 * Expo 호출은 PushGateway를 가짜로 바꿔 대신한다. Docker가 실행 중이어야 한다.
 */
@SpringBootTest(
		properties = {
			"morak.auth.jwt.secret=integration-test-secret-integration-test-secret",
			"morak.push.expo.enabled=true",
			"morak.push.expo.receipt-delay=PT0.3S"
		})
class PushDispatchIntegrationTest {

	private static final Long USER = 10L;

	@TestConfiguration(proxyBeanMethods = false)
	static class Containers {

		@Bean
		@ServiceConnection
		PostgreSQLContainer postgres() {
			return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
		}
	}

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private PushTokenService pushTokenService;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private JdbcClient jdbc;

	@MockitoBean
	private PushGateway gateway;

	@BeforeEach
	void setUp() {
		jdbc.sql("DELETE FROM morak.notification").update();
		jdbc.sql("DELETE FROM morak.push_token").update();
		jdbc.sql("DELETE FROM morak.push_subscription").update();
	}

	private static NotifyCommand command(String dedupKey) {
		return new NotifyCommand(USER, new ParticipationApproved("매일 아침 러닝"), "CHALLENGE", 5L, dedupKey);
	}

	private void commitNotify(NotifyCommand command) {
		transactionTemplate.executeWithoutResult(status -> notificationService.notify(command));
	}

	private long count(String table) {
		return jdbc.sql("SELECT count(*) FROM morak." + table).query(Long.class).single();
	}

	private void awaitCount(String table, long expected) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 5_000;
		while (count(table) != expected && System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}
		assertEquals(expected, count(table));
	}

	@Test
	void committedNotificationIsPushedWithTitleBodyAndUrl() {
		pushTokenService.register(USER, "ExponentPushToken[device-1]", null);
		when(gateway.send(any())).thenReturn(List.of(PushTicket.accepted("t1")));

		commitNotify(command("K:1"));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<PushMessage>> captor = ArgumentCaptor.forClass(List.class);
		verify(gateway, timeout(5_000)).send(captor.capture());
		assertEquals(
				List.of(new PushMessage(
						"ExponentPushToken[device-1]", "참여 확정", "매일 아침 러닝 참여가 확정됐어요", "/challenges/5")),
				captor.getValue());
		assertEquals(1, count("notification"));
	}

	@Test
	void duplicateNotificationIsPushedOnlyOnce() {
		pushTokenService.register(USER, "ExponentPushToken[device-1]", null);
		when(gateway.send(any())).thenReturn(List.of(PushTicket.accepted("t1")));

		commitNotify(command("K:dup"));
		commitNotify(command("K:dup"));

		verify(gateway, after(1_000).times(1)).send(any());
		assertEquals(1, count("notification"));
	}

	@Test
	void rolledBackNotificationIsNotPushed() {
		pushTokenService.register(USER, "ExponentPushToken[device-1]", null);

		assertThrows(IllegalStateException.class, () -> transactionTemplate.executeWithoutResult(status -> {
			notificationService.notify(command("K:rollback"));
			throw new IllegalStateException("호출한 도메인 실패");
		}));

		verify(gateway, after(1_000).never()).send(any());
		assertEquals(0, count("notification"));
	}

	@Test
	void notPushedWhenRecipientTurnedPushOff() {
		pushTokenService.register(USER, "ExponentPushToken[device-1]", null);
		jdbc.sql("UPDATE morak.push_subscription SET enabled = FALSE").update();

		commitNotify(command("K:off"));

		verify(gateway, after(1_000).never()).send(any());
		assertEquals(1, count("notification"));
	}

	@Test
	void notPushedWhenRecipientHasNoToken() {
		commitNotify(command("K:no-token"));

		verify(gateway, after(1_000).never()).send(any());
		assertEquals(1, count("notification"));
	}

	@Test
	void tokenRejectedAsDeviceNotRegisteredIsDeleted() throws Exception {
		pushTokenService.register(USER, "ExponentPushToken[gone]", null);
		when(gateway.send(any())).thenReturn(List.of(PushTicket.rejected(PushGateway.DEVICE_NOT_REGISTERED)));

		commitNotify(command("K:gone"));

		awaitCount("push_token", 0);
		assertEquals(1, count("notification"));
	}

	@Test
	void tokenWhoseReceiptSaysDeviceNotRegisteredIsDeletedAfterDelay() throws Exception {
		pushTokenService.register(USER, "ExponentPushToken[later-gone]", null);
		when(gateway.send(any())).thenReturn(List.of(PushTicket.accepted("ticket-1")));
		when(gateway.getReceipts(any()))
				.thenReturn(Map.of("ticket-1", PushReceipt.failed(PushGateway.DEVICE_NOT_REGISTERED)));

		commitNotify(command("K:receipt"));

		awaitCount("push_token", 0);
		verify(gateway, timeout(5_000).times(1)).getReceipts(List.of("ticket-1"));
	}

	@Test
	void tokenWithOkReceiptIsKept() throws Exception {
		pushTokenService.register(USER, "ExponentPushToken[healthy]", null);
		when(gateway.send(any())).thenReturn(List.of(PushTicket.accepted("ticket-1")));
		when(gateway.getReceipts(any())).thenReturn(Map.of("ticket-1", PushReceipt.ok()));

		commitNotify(command("K:healthy"));

		verify(gateway, timeout(5_000)).getReceipts(any());
		Thread.sleep(300);
		assertEquals(1, count("push_token"));
	}

	@Test
	void pushFailureDoesNotAffectStoredNotificationOrCaller() {
		pushTokenService.register(USER, "ExponentPushToken[device-1]", null);
		when(gateway.send(any())).thenThrow(new IllegalStateException("Expo down"));

		boolean created = Boolean.TRUE.equals(
				transactionTemplate.execute(status -> notificationService.notify(command("K:fail"))));

		assertTrue(created);
		verify(gateway, timeout(5_000)).send(any());
		assertEquals(1, count("notification"));
		assertEquals(1, count("push_token"));
		verify(gateway, never()).getReceipts(any());
	}
}
