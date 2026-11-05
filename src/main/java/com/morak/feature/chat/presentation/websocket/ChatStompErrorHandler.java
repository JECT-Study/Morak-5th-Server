package com.morak.feature.chat.presentation.websocket;

import com.morak.common.exception.BusinessException;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/**
 * 인증·권한 검사에서 거부되면 ERROR 프레임으로 알린다.
 * 기본 처리기는 "Failed to send message..." 같은 일반 문구만 보내므로, 프론트가 원인을 구분할 수 있게
 * BusinessException의 메시지(message 헤더)와 에러 코드(code 헤더)를 담는다. ERROR 프레임을 받은 연결은 서버가 닫는다.
 */
public class ChatStompErrorHandler extends StompSubProtocolErrorHandler {

	static final String CODE_HEADER = "code";
	private static final byte[] EMPTY_PAYLOAD = new byte[0];

	@Override
	public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
		BusinessException exception = findBusinessException(ex);
		if (exception == null) {
			return super.handleClientMessageProcessingError(clientMessage, ex);
		}
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
		accessor.setMessage(exception.getMessage());
		accessor.setNativeHeader(CODE_HEADER, exception.getErrorCode().code());
		StompHeaderAccessor clientAccessor = MessageHeaderAccessor.getAccessor(clientMessage, StompHeaderAccessor.class);
		if (clientAccessor != null && clientAccessor.getReceipt() != null) {
			accessor.setReceiptId(clientAccessor.getReceipt());
		}
		return MessageBuilder.createMessage(EMPTY_PAYLOAD, accessor.getMessageHeaders());
	}

	private BusinessException findBusinessException(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof BusinessException exception) {
				return exception;
			}
		}
		return null;
	}
}
