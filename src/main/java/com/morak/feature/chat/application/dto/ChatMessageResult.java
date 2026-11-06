package com.morak.feature.chat.application.dto;

import com.morak.feature.chat.domain.entity.ChatMessageType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param sender 시스템 메시지이거나 보낸 사람을 찾을 수 없으면 null
 * @param reply 인증 스토리 답글일 때만 있다
 */
public record ChatMessageResult(
		Long id,
		ChatMessageType type,
		UUID clientMessageId,
		Sender sender,
		String content,
		Reply reply,
		Instant createdAt) {

	public record Sender(Long userId, String nickname, String profileImageUrl, String role) {}

	/** 원본 인증을 찾을 수 없으면 authorNickname, certDate는 null이다. */
	public record Reply(Long certificationId, String authorNickname, LocalDate certDate) {}
}
