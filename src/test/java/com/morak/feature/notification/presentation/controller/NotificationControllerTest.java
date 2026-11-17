package com.morak.feature.notification.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.morak.common.config.SecurityConfig;
import com.morak.common.exception.BusinessException;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.auth.application.service.AuthenticateUserService;
import com.morak.feature.auth.domain.vo.AuthUserId;
import com.morak.feature.auth.infrastructure.security.LoginUserJwtConverter;
import com.morak.feature.notification.application.dto.NotificationListResult;
import com.morak.feature.notification.application.service.NotificationInboxService;
import com.morak.feature.notification.domain.entity.NotificationType;
import com.morak.feature.notification.domain.exception.NotificationErrorCode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(
		controllers = NotificationController.class,
		properties = "morak.auth.jwt.secret=01234567890123456789012345678901")
@Import({SecurityConfig.class, LoginUserJwtConverter.class})
class NotificationControllerTest {

	private static final long USER_ID = 7L;

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private NotificationInboxService inbox;

	@MockitoBean
	private AuthenticateUserService authenticateUserService;

	@MockitoBean
	private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMappingContext;

	private static RequestPostProcessor loggedIn(boolean onboarded) {
		return authentication(new UsernamePasswordAuthenticationToken(
				new LoginUser(new AuthUserId(USER_ID), onboarded), null, List.of()));
	}

	@Test
	void listReturnsNotificationsWithTargetAndCursor() throws Exception {
		when(inbox.getNotifications(USER_ID, 30L, 2))
				.thenReturn(new NotificationListResult(
						List.of(new NotificationListResult.Entry(
								29L,
								NotificationType.PARTICIPATION_APPROVED,
								"참여 확정",
								"매일 아침 러닝 참여가 확정됐어요",
								"CHALLENGE",
								5L,
								false,
								Instant.parse("2026-11-17T03:00:00Z"))),
						29L));

		mvc.perform(get("/notifications").param("before", "30").param("limit", "2").with(loggedIn(true)))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.nextCursor").value(29))
				.andExpect(jsonPath("$.data.notifications[0].id").value(29))
				.andExpect(jsonPath("$.data.notifications[0].type").value("PARTICIPATION_APPROVED"))
				.andExpect(jsonPath("$.data.notifications[0].title").value("참여 확정"))
				.andExpect(jsonPath("$.data.notifications[0].body").value("매일 아침 러닝 참여가 확정됐어요"))
				.andExpect(jsonPath("$.data.notifications[0].targetType").value("CHALLENGE"))
				.andExpect(jsonPath("$.data.notifications[0].targetId").value(5))
				.andExpect(jsonPath("$.data.notifications[0].read").value(false))
				.andExpect(jsonPath("$.data.notifications[0].createdAt").value("2026-11-17T03:00:00Z"));
	}

	@Test
	void listWithoutParamsPassesNulls() throws Exception {
		when(inbox.getNotifications(USER_ID, null, null)).thenReturn(new NotificationListResult(List.of(), null));

		mvc.perform(get("/notifications").with(loggedIn(true)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.notifications").isEmpty())
				.andExpect(jsonPath("$.data.nextCursor").isEmpty());
	}

	@Test
	void invalidCursorReturnsBadRequest() throws Exception {
		when(inbox.getNotifications(USER_ID, 0L, null))
				.thenThrow(new BusinessException(NotificationErrorCode.INVALID_CURSOR));

		mvc.perform(get("/notifications").param("before", "0").with(loggedIn(true)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("NOTIFICATION_002"));
	}

	@Test
	void unreadCountReturnsCount() throws Exception {
		when(inbox.countUnread(USER_ID)).thenReturn(3L);

		mvc.perform(get("/notifications/unread-count").with(loggedIn(true)))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.data.unreadCount").value(3));
	}

	@Test
	void markReadCallsServiceWithLoggedInUser() throws Exception {
		mvc.perform(patch("/notifications/42/read").with(loggedIn(true)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(inbox).markRead(USER_ID, 42L);
	}

	@Test
	void markReadOfMissingNotificationReturnsNotFound() throws Exception {
		doThrow(new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND))
				.when(inbox)
				.markRead(USER_ID, 999L);

		mvc.perform(patch("/notifications/999/read").with(loggedIn(true)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOTIFICATION_003"));
	}

	@Test
	void markAllReadCallsServiceWithLoggedInUser() throws Exception {
		mvc.perform(patch("/notifications/read-all").with(loggedIn(true)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(inbox).markAllRead(USER_ID);
	}

	@Test
	void requestsWithoutLoginAreRejected() throws Exception {
		mvc.perform(get("/notifications")).andExpect(status().isUnauthorized());
		mvc.perform(get("/notifications/unread-count")).andExpect(status().isUnauthorized());
		mvc.perform(patch("/notifications/1/read")).andExpect(status().isUnauthorized());
		mvc.perform(patch("/notifications/read-all")).andExpect(status().isUnauthorized());

		verifyNoInteractions(inbox);
	}

	@Test
	void membersWhoHaveNotFinishedOnboardingAreForbidden() throws Exception {
		mvc.perform(get("/notifications").with(loggedIn(false))).andExpect(status().isForbidden());

		verifyNoInteractions(inbox);
	}
}
