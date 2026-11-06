package com.morak.feature.chat.presentation.controller;

import com.morak.common.response.ApiResponse;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.chat.application.service.ChatMessageService;
import com.morak.feature.chat.application.service.ChatTicketService;
import com.morak.feature.chat.presentation.dto.response.ChatMessageResponse;
import com.morak.feature.chat.presentation.dto.response.ChatTicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "채팅", description = "챌린지 채팅 API")
public class ChatController {

	private final ChatMessageService chatMessageService;
	private final ChatTicketService chatTicketService;

	public ChatController(ChatMessageService chatMessageService, ChatTicketService chatTicketService) {
		this.chatMessageService = chatMessageService;
		this.chatTicketService = chatTicketService;
	}

	@PostMapping("/chat/ws-ticket")
	@Operation(summary = "웹소켓 연결 티켓 발급",
			description = "BFF가 호출합니다. 30초 동안 1번만 쓸 수 있는 티켓을 반환하며, 브라우저는 /ws 연결 후 STOMP CONNECT 헤더 ticket으로 전달합니다.")
	public ResponseEntity<ApiResponse<ChatTicketResponse>> issueTicket(@AuthenticationPrincipal LoginUser loginUser) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(ApiResponse.success(ChatTicketResponse.from(chatTicketService.issue(userId(loginUser)))));
	}

	@GetMapping("/challenges/{challengeId}/chat/messages")
	@Operation(summary = "채팅 메시지 조회",
			description = "id 오름차순으로 반환합니다. before(이전 메시지), after(새 메시지) 커서 중 하나만 사용할 수 있고, 둘 다 없으면 최신 메시지부터 조회합니다. "
					+ "메시지 전송과 실시간 수신은 STOMP이며 docs/api/chat-api.md를 참고하세요.")
	public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> list(
			@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable Long challengeId,
			@Parameter(description = "이 id보다 오래된 메시지") @RequestParam(required = false) Long before,
			@Parameter(description = "이 id보다 새로운 메시지") @RequestParam(required = false) Long after,
			@Parameter(description = "조회 개수 (기본 30, 최대 100)") @RequestParam(required = false) Integer limit) {
		List<ChatMessageResponse> messages = chatMessageService
				.list(challengeId, userId(loginUser), before, after, limit).stream()
				.map(ChatMessageResponse::from)
				.toList();
		return ResponseEntity.ok(ApiResponse.success(messages));
	}

	private Long userId(LoginUser loginUser) {
		return loginUser.userId().value();
	}
}
