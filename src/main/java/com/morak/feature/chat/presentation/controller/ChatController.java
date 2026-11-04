package com.morak.feature.chat.presentation.controller;

import com.morak.common.response.ApiResponse;
import com.morak.feature.chat.application.service.ChatMessageService;
import com.morak.feature.chat.presentation.dto.response.ChatMessageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "채팅", description = "챌린지 채팅 API")
public class ChatController {

	private final ChatMessageService chatMessageService;

	public ChatController(ChatMessageService chatMessageService) {
		this.chatMessageService = chatMessageService;
	}

	@GetMapping("/challenges/{challengeId}/chat/messages")
	@Operation(summary = "채팅 메시지 조회",
			description = "id 오름차순으로 반환합니다. before(이전 메시지), after(새 메시지) 커서 중 하나만 사용할 수 있고, 둘 다 없으면 최신 메시지부터 조회합니다.")
	public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> list(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long challengeId,
			@Parameter(description = "이 id보다 오래된 메시지") @RequestParam(required = false) Long before,
			@Parameter(description = "이 id보다 새로운 메시지") @RequestParam(required = false) Long after,
			@Parameter(description = "조회 개수 (기본 30, 최대 100)") @RequestParam(required = false) Integer limit) {
		List<ChatMessageResponse> messages = chatMessageService
				.list(challengeId, userId(jwt), before, after, limit).stream()
				.map(ChatMessageResponse::from)
				.toList();
		return ResponseEntity.ok(ApiResponse.success(messages));
	}

	@DeleteMapping("/chat/messages/{messageId}")
	@Operation(summary = "채팅 메시지 삭제 (미구현)", description = "작성자·방장만 가능 예정. 현재는 501을 반환합니다.")
	public ResponseEntity<ApiResponse<Map<String, Object>>> delete(
			@AuthenticationPrincipal Jwt jwt, @PathVariable Long messageId) {
		chatMessageService.delete(messageId, userId(jwt));
		return ResponseEntity.ok(ApiResponse.successEmpty());
	}

	private Long userId(Jwt jwt) {
		return Long.valueOf(jwt.getSubject());
	}
}
