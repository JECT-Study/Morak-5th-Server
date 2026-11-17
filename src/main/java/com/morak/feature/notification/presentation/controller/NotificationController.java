package com.morak.feature.notification.presentation.controller;

import com.morak.common.config.OpenApiConfig;
import com.morak.common.response.ApiResponse;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.notification.application.service.NotificationInboxService;
import com.morak.feature.notification.presentation.dto.response.NotificationListResponse;
import com.morak.feature.notification.presentation.dto.response.UnreadCountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
@Tag(name = "알림", description = "앱 안 알림함 조회·읽음 처리 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class NotificationController {

	private final NotificationInboxService inboxService;

	public NotificationController(NotificationInboxService inboxService) {
		this.inboxService = inboxService;
	}

	@GetMapping
	@Operation(summary = "내 알림 목록 조회",
			description = "최신순으로 반환합니다. 다음 페이지는 응답의 nextCursor를 before로 넘겨 조회합니다. "
					+ "알림을 눌렀을 때 이동할 곳은 targetType과 targetId로 알 수 있습니다.")
	public ResponseEntity<ApiResponse<NotificationListResponse>> getNotifications(
			@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "이 id보다 오래된 알림") @RequestParam(required = false) Long before,
			@Parameter(description = "조회 개수 (기본 20, 최대 100)") @RequestParam(required = false) Integer limit) {
		NotificationListResponse body = NotificationListResponse.from(
				inboxService.getNotifications(loginUser.userId().value(), before, limit));
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
	}

	@GetMapping("/unread-count")
	@Operation(summary = "읽지 않은 알림 개수 조회", description = "홈 우측 상단 종 아이콘에 표시할 개수입니다.")
	public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
			@AuthenticationPrincipal LoginUser loginUser) {
		UnreadCountResponse body = new UnreadCountResponse(inboxService.countUnread(loginUser.userId().value()));
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
	}

	@PatchMapping("/{notificationId}/read")
	@Operation(summary = "알림 읽음 처리",
			description = "알림 한 건을 읽음 처리합니다. 이미 읽은 알림이면 그대로 성공합니다. "
					+ "없는 알림과 다른 사람의 알림은 404입니다.")
	public ApiResponse<Map<String, Object>> markRead(
			@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long notificationId) {
		inboxService.markRead(loginUser.userId().value(), notificationId);
		return ApiResponse.successEmpty();
	}

	@PatchMapping("/read-all")
	@Operation(summary = "알림 전체 읽음 처리", description = "읽지 않은 내 알림을 모두 읽음 처리합니다.")
	public ApiResponse<Map<String, Object>> markAllRead(@AuthenticationPrincipal LoginUser loginUser) {
		inboxService.markAllRead(loginUser.userId().value());
		return ApiResponse.successEmpty();
	}
}
