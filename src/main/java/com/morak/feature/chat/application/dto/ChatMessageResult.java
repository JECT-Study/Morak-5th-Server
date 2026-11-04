package com.morak.feature.chat.application.dto;

import com.morak.feature.chat.domain.entity.ChatMessage;
import java.time.LocalDateTime;

public record ChatMessageResult(Long id, Long senderId, String content, LocalDateTime createdAt, boolean deleted) {

	/** 삭제된 메시지는 내용을 노출하지 않는다. */
	public static ChatMessageResult of(ChatMessage message, Long senderId) {
		return new ChatMessageResult(
				message.getId(),
				senderId,
				message.isDeleted() ? null : message.getContent(),
				message.getCreatedAt(),
				message.isDeleted());
	}
}
