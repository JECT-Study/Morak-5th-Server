package com.morak.feature.chat.domain.exception;

import com.morak.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ChatErrorCode implements ErrorCode {
	NOT_PARTICIPANT("CHAT_001", "채팅방에 참여 중인 참가자가 아닙니다.", HttpStatus.FORBIDDEN),
	CHAT_CLOSED("CHAT_002", "종료된 챌린지의 채팅방입니다.", HttpStatus.FORBIDDEN),
	CHALLENGE_NOT_FOUND("CHAT_003", "챌린지를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	INVALID_CURSOR("CHAT_005", "before와 after는 함께 사용할 수 없습니다.", HttpStatus.BAD_REQUEST),
	INVALID_MESSAGE("CHAT_006", "메시지는 1자 이상 1000자 이하여야 합니다.", HttpStatus.BAD_REQUEST),
	INVALID_TICKET("CHAT_008", "유효하지 않거나 만료된 채팅 연결 티켓입니다.", HttpStatus.UNAUTHORIZED),
	UNAUTHENTICATED("CHAT_009", "인증되지 않은 채팅 연결입니다.", HttpStatus.UNAUTHORIZED),
	FORBIDDEN_DESTINATION("CHAT_010", "구독하거나 보낼 수 없는 경로입니다.", HttpStatus.FORBIDDEN),
	SEND_FORBIDDEN("CHAT_011", "지금은 메시지를 보낼 수 없습니다. 채팅 기록만 볼 수 있어요.", HttpStatus.FORBIDDEN),
	INVALID_REPLY_TARGET("CHAT_012", "답글을 보낼 인증을 찾을 수 없습니다.", HttpStatus.BAD_REQUEST),
	INVALID_CLIENT_MESSAGE_ID("CHAT_013", "clientMessageId는 UUID 형식이어야 합니다.", HttpStatus.BAD_REQUEST);

	private final String code;
	private final String message;
	private final HttpStatus status;

	ChatErrorCode(String code, String message, HttpStatus status) {
		this.code = code;
		this.message = message;
		this.status = status;
	}

	@Override public String code() { return code; }
	@Override public String message() { return message; }
	@Override public HttpStatus status() { return status; }
}
