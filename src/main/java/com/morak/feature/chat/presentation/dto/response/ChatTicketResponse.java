package com.morak.feature.chat.presentation.dto.response;

import com.morak.feature.chat.application.dto.ChatTicketResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record ChatTicketResponse(
		@Schema(description = "웹소켓 연결용 1회용 티켓. STOMP CONNECT 헤더 ticket으로 전달")
		String ticket,
		@Schema(description = "티켓 만료까지 남은 시간(초)", example = "30")
		long expiresIn) {

	public static ChatTicketResponse from(ChatTicketResult result) {
		return new ChatTicketResponse(result.ticket(), result.expiresIn());
	}
}
