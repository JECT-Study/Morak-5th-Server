package com.morak.feature.chat.presentation.dto.response;

import com.morak.feature.chat.application.dto.ChatMessageResult;
import java.time.LocalDateTime;

public record ChatMessageResponse(Long id, Long senderId, String content, LocalDateTime createdAt, boolean deleted) {

	public static ChatMessageResponse from(ChatMessageResult result) {
		return new ChatMessageResponse(
				result.id(), result.senderId(), result.content(), result.createdAt(), result.deleted());
	}
}
