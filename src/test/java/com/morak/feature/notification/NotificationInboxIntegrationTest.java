package com.morak.feature.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.common.exception.BusinessException;
import com.morak.feature.notification.application.dto.NotificationContent.ParticipationApproved;
import com.morak.feature.notification.application.dto.NotificationListResult;
import com.morak.feature.notification.application.dto.NotifyCommand;
import com.morak.feature.notification.application.service.NotificationInboxService;
import com.morak.feature.notification.application.service.NotificationService;
import com.morak.feature.notification.domain.exception.NotificationErrorCode;
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
 * 실제 PostgreSQL(Testcontainers)에서 목록 쿼리, 읽지 않은 개수, 읽음 처리 UPDATE가 본인 알림에만 적용되는지 확인한다.
 * Docker가 실행 중이어야 한다.
 */
@SpringBootTest(properties = "morak.auth.jwt.secret=integration-test-secret-integration-test-secret")
class NotificationInboxIntegrationTest {

	private static final Long USER = 10L;
	private static final Long OTHER = 20L;

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
	private NotificationInboxService inbox;

	@Autowired
	private JdbcClient jdbc;

	@BeforeEach
	void setUp() {
		jdbc.sql("DELETE FROM morak.notification").update();
	}

	private void add(Long recipient, int count) {
		for (int i = 0; i < count; i++) {
			notificationService.notify(new NotifyCommand(
					recipient, new ParticipationApproved("챌린지" + i), "CHALLENGE", 5L, "K:" + recipient + ":" + i));
		}
	}

	private static List<String> titlesOf(NotificationListResult result) {
		return result.notifications().stream().map(NotificationListResult.Entry::body).toList();
	}

	@Test
	void listsMyNotificationsNewestFirstWithCursorPaging() {
		add(USER, 5);
		add(OTHER, 3);

		NotificationListResult first = inbox.getNotifications(USER, null, 2);
		assertEquals(List.of("챌린지4 참여가 확정됐어요", "챌린지3 참여가 확정됐어요"), titlesOf(first));
		assertEquals(first.notifications().get(1).id(), first.nextCursor());

		NotificationListResult second = inbox.getNotifications(USER, first.nextCursor(), 2);
		assertEquals(List.of("챌린지2 참여가 확정됐어요", "챌린지1 참여가 확정됐어요"), titlesOf(second));

		NotificationListResult last = inbox.getNotifications(USER, second.nextCursor(), 2);
		assertEquals(List.of("챌린지0 참여가 확정됐어요"), titlesOf(last));
		assertNull(last.nextCursor());
	}

	@Test
	void listReturnsTargetAndUnreadState() {
		add(USER, 1);

		NotificationListResult.Entry entry = inbox.getNotifications(USER, null, null).notifications().get(0);

		assertEquals("CHALLENGE", entry.targetType());
		assertEquals(5L, entry.targetId());
		assertFalse(entry.read());
	}

	@Test
	void countsOnlyMyUnreadNotifications() {
		add(USER, 3);
		add(OTHER, 4);

		assertEquals(3, inbox.countUnread(USER));
		assertEquals(4, inbox.countUnread(OTHER));
	}

	@Test
	void markReadChangesOnlyThatNotificationAndKeepsFirstReadTime() {
		add(USER, 3);
		Long target = inbox.getNotifications(USER, null, null).notifications().get(1).id();

		inbox.markRead(USER, target);
		Object firstReadAt = jdbc.sql("SELECT read_at FROM morak.notification WHERE id = :id")
				.param("id", target)
				.query()
				.singleValue();
		inbox.markRead(USER, target);
		Object secondReadAt = jdbc.sql("SELECT read_at FROM morak.notification WHERE id = :id")
				.param("id", target)
				.query()
				.singleValue();

		assertEquals(2, inbox.countUnread(USER));
		assertEquals(firstReadAt, secondReadAt);
		List<NotificationListResult.Entry> entries = inbox.getNotifications(USER, null, null).notifications();
		assertFalse(entries.get(0).read());
		assertTrue(entries.get(1).read());
		assertFalse(entries.get(2).read());
	}

	@Test
	void markReadRejectsOthersAndMissingNotificationsWithoutChangingThem() {
		add(OTHER, 1);
		Long othersId = inbox.getNotifications(OTHER, null, null).notifications().get(0).id();

		BusinessException others = assertThrows(BusinessException.class, () -> inbox.markRead(USER, othersId));
		BusinessException missing = assertThrows(BusinessException.class, () -> inbox.markRead(USER, 999_999L));

		assertEquals(NotificationErrorCode.NOTIFICATION_NOT_FOUND, others.getErrorCode());
		assertEquals(NotificationErrorCode.NOTIFICATION_NOT_FOUND, missing.getErrorCode());
		assertEquals(1, inbox.countUnread(OTHER));
	}

	@Test
	void markAllReadChangesOnlyMyUnreadNotifications() {
		add(USER, 3);
		add(OTHER, 2);
		Long first = inbox.getNotifications(USER, null, null).notifications().get(2).id();
		inbox.markRead(USER, first);

		int changed = inbox.markAllRead(USER);

		assertEquals(2, changed);
		assertEquals(0, inbox.countUnread(USER));
		assertEquals(2, inbox.countUnread(OTHER));
		assertEquals(0, inbox.markAllRead(USER));
	}
}
