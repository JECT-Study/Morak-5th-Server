package com.morak.feature.chat.presentation.websocket;

import com.morak.common.exception.BusinessException;
import com.morak.feature.chat.application.service.ChatSubscriptionService;
import com.morak.feature.chat.domain.exception.ChatErrorCode;
import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

/**
 * STOMP SUBSCRIBE 때 구독 권한을 검사한다.
 * 허용 경로는 아래 두 개뿐이며, 그 외 경로는 모두 거부한다.
 * - /topic/challenges/{challengeId}/chat : 해당 챌린지 채팅방 참가자만
 * - /user/queue/errors : 로그인한 사용자 누구나 (본인에게만 전달되는 경로)
 */
public class ChatSubscribeInterceptor implements ChannelInterceptor {

	private static final Pattern CHAT_TOPIC = Pattern.compile("^/topic/challenges/(\\d{1,18})/chat$");
	private static final String ERROR_QUEUE = "/user/queue/errors";

	private final ChatSubscriptionService subscriptionService;

	public ChatSubscribeInterceptor(ChatSubscriptionService subscriptionService) {
		this.subscriptionService = subscriptionService;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null || accessor.getCommand() != StompCommand.SUBSCRIBE) {
			return message;
		}
		Principal user = accessor.getUser();
		if (!(user instanceof ChatPrincipal principal)) {
			throw new BusinessException(ChatErrorCode.UNAUTHENTICATED);
		}
		authorize(accessor.getDestination(), principal.userId());
		return message;
	}

	private void authorize(String destination, Long userId) {
		if (ERROR_QUEUE.equals(destination)) {
			return;
		}
		Matcher matcher = destination == null ? null : CHAT_TOPIC.matcher(destination);
		if (matcher == null || !matcher.matches()) {
			throw new BusinessException(ChatErrorCode.FORBIDDEN_DESTINATION);
		}
		subscriptionService.checkSubscribable(Long.valueOf(matcher.group(1)), userId);
	}
}
