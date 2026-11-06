package com.morak.feature.chat.presentation.dto.response;

import com.morak.feature.chat.application.dto.ChatMessageResult;
import com.morak.feature.chat.domain.entity.ChatMessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ChatMessageResponse(
		@Schema(description = "메시지 ID. 오름차순이 시간순이며 중복 제거와 커서 기준으로 쓴다", example = "101")
		Long id,
		@Schema(description = "USER: 참가자 메시지, SYSTEM: 서버 안내 메시지")
		ChatMessageType type,
		@Schema(description = "보낸 사람이 정한 UUID. 내가 보낸 메시지를 찾는 데 쓴다. SYSTEM이면 null", nullable = true)
		UUID clientMessageId,
		@Schema(description = "보낸 사람. SYSTEM이면 null", nullable = true)
		SenderResponse sender,
		@Schema(description = "메시지 내용 (1~1000자)", example = "안녕하세요!")
		String content,
		@Schema(description = "인증 스토리 답글일 때만 있는 인용 정보", nullable = true)
		ReplyResponse reply,
		@Schema(description = "작성 시각 (UTC, 화면에는 KST로 변환)", example = "2026-11-05T06:00:00Z")
		Instant createdAt) {

	public static ChatMessageResponse from(ChatMessageResult result) {
		return new ChatMessageResponse(
				result.id(),
				result.type(),
				result.clientMessageId(),
				result.sender() == null ? null : SenderResponse.from(result.sender()),
				result.content(),
				result.reply() == null ? null : ReplyResponse.from(result.reply()),
				result.createdAt());
	}

	public record SenderResponse(
			@Schema(description = "보낸 사람 사용자 ID", example = "7")
			Long userId,
			@Schema(description = "닉네임", nullable = true)
			String nickname,
			@Schema(description = "프로필 이미지 URL", nullable = true)
			String profileImageUrl,
			@Schema(description = "챌린지 내 역할: HOST(방장), DEPUTY(부방장), MEMBER(참여자)", example = "HOST")
			String role) {

		static SenderResponse from(ChatMessageResult.Sender sender) {
			return new SenderResponse(sender.userId(), sender.nickname(), sender.profileImageUrl(), sender.role());
		}
	}

	public record ReplyResponse(
			@Schema(description = "원본 인증 ID. 선택하면 이 인증의 스토리 화면으로 이동", example = "55")
			Long certificationId,
			@Schema(description = "원본 인증 작성자 닉네임. 원본을 찾을 수 없으면 null", nullable = true)
			String authorNickname,
			@Schema(description = "원본 인증 날짜(KST). 원본을 찾을 수 없으면 null", example = "2026-11-04", nullable = true)
			LocalDate certDate) {

		static ReplyResponse from(ChatMessageResult.Reply reply) {
			return new ReplyResponse(reply.certificationId(), reply.authorNickname(), reply.certDate());
		}
	}
}
