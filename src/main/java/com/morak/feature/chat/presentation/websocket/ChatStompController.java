package com.morak.feature.chat.presentation.websocket;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.common.exception.ErrorCode;
import com.morak.feature.chat.application.dto.ChatMessageResult;
import com.morak.feature.chat.application.service.ChatMessageService;
import com.morak.feature.chat.domain.exception.ChatErrorCode;
import com.morak.feature.chat.presentation.dto.request.ChatSendRequest;
import com.morak.feature.chat.presentation.dto.response.ChatMessageResponse;
import com.morak.feature.chat.presentation.dto.response.ChatSendErrorResponse;
import java.security.Principal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

/**
 * 채팅 메시지 전송(STOMP SEND). 저장에 성공하면 방 전체(/topic)로 내보내고,
 * 실패하면 보낸 사람에게만(/user/queue/errors) 알려 연결은 유지한다.
 */
@Controller
public class ChatStompController {

	static final String ERROR_QUEUE = "/queue/errors";
	private static final Logger log = LoggerFactory.getLogger(ChatStompController.class);

	private final ChatMessageService chatMessageService;
	private final SimpMessagingTemplate messagingTemplate;

	public ChatStompController(ChatMessageService chatMessageService, SimpMessagingTemplate messagingTemplate) {
		this.chatMessageService = chatMessageService;
		this.messagingTemplate = messagingTemplate;
	}

	@MessageMapping("/challenges/{challengeId}/chat")
	public void send(
			@DestinationVariable Long challengeId, @Payload ChatSendRequest request, Principal principal) {
		if (!(principal instanceof ChatPrincipal chatPrincipal)) {
			throw new BusinessException(ChatErrorCode.UNAUTHENTICATED);
		}
		try {
			ChatMessageResult result = sendWithRetry(challengeId, chatPrincipal.userId(), request);
			messagingTemplate.convertAndSend(topic(challengeId), ChatMessageResponse.from(result));
		} catch (BusinessException exception) {
			replyError(chatPrincipal, request.clientMessageId(), exception.getErrorCode());
		} catch (RuntimeException exception) {
			log.error("채팅 메시지 전송 실패 challengeId={} userId={}", challengeId, chatPrincipal.userId(), exception);
			replyError(chatPrincipal, request.clientMessageId(), CommonErrorCode.INTERNAL_SERVER_ERROR);
		}
	}

	/** 본문을 읽지 못하는 등 요청 자체가 잘못된 경우. 어느 메시지인지 알 수 없어 clientMessageId는 null로 보낸다. */
	@MessageExceptionHandler
	@SendToUser(destinations = ERROR_QUEUE, broadcast = false)
	public ChatSendErrorResponse handleMalformed(Exception exception) {
		log.warn("잘못된 채팅 전송 요청: {}", exception.getMessage());
		ErrorCode errorCode = CommonErrorCode.INVALID_REQUEST;
		return new ChatSendErrorResponse(null, errorCode.code(), errorCode.message());
	}

	/**
	 * 같은 clientMessageId를 거의 동시에 두 번 보내면 유니크 제약에 걸릴 수 있다.
	 * 이때는 먼저 저장된 메시지가 있으므로 한 번 더 호출하면 그 메시지를 돌려받는다(별도 트랜잭션이어야 해서 여기서 처리).
	 */
	private ChatMessageResult sendWithRetry(Long challengeId, Long userId, ChatSendRequest request) {
		UUID clientMessageId = parseClientMessageId(request.clientMessageId());
		try {
			return chatMessageService.send(
					challengeId, userId, clientMessageId, request.content(), request.replyCertificationId());
		} catch (DataIntegrityViolationException duplicated) {
			return chatMessageService.send(
					challengeId, userId, clientMessageId, request.content(), request.replyCertificationId());
		}
	}

	private UUID parseClientMessageId(String value) {
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException | NullPointerException invalid) {
			throw new BusinessException(ChatErrorCode.INVALID_CLIENT_MESSAGE_ID);
		}
	}

	private void replyError(ChatPrincipal principal, String clientMessageId, ErrorCode errorCode) {
		messagingTemplate.convertAndSendToUser(
				principal.getName(),
				ERROR_QUEUE,
				new ChatSendErrorResponse(clientMessageId, errorCode.code(), errorCode.message()));
	}

	private String topic(Long challengeId) {
		return "/topic/challenges/" + challengeId + "/chat";
	}
}
