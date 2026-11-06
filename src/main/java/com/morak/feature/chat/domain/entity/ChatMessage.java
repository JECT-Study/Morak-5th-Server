package com.morak.feature.chat.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "chat_message")
public class ChatMessage {

	public static final int MAX_CONTENT_LENGTH = 1000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "challenge_id", nullable = false)
	private Long challengeId;

	/** 시스템 메시지는 보낸 참가자가 없어 null이다. */
	@Column(name = "participation_id")
	private Long participationId;

	@Enumerated(EnumType.STRING)
	@Column(name = "message_type", nullable = false, length = 10)
	private ChatMessageType type;

	/** 클라이언트가 만든 UUID. (participation_id, client_message_id)가 유니크라 재전송해도 한 번만 저장된다. */
	@Column(name = "client_message_id")
	private UUID clientMessageId;

	/** 인증 스토리 답글이면 원본 인증 ID. 다른 도메인이라 FK 없이 ID만 저장한다. */
	@Column(name = "reply_certification_id")
	private Long replyCertificationId;

	@Column(nullable = false, length = MAX_CONTENT_LENGTH)
	private String content;

	/** UTC 시각 (백엔드 위키 §3: DATETIME은 UTC). */
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected ChatMessage() {}

	private ChatMessage(
			Long challengeId,
			Long participationId,
			ChatMessageType type,
			UUID clientMessageId,
			Long replyCertificationId,
			String content,
			LocalDateTime createdAt) {
		this.challengeId = challengeId;
		this.participationId = participationId;
		this.type = type;
		this.clientMessageId = clientMessageId;
		this.replyCertificationId = replyCertificationId;
		this.content = content;
		this.createdAt = createdAt;
	}

	public static ChatMessage user(
			Long challengeId,
			Long participationId,
			UUID clientMessageId,
			String content,
			Long replyCertificationId,
			LocalDateTime createdAtUtc) {
		return new ChatMessage(
				challengeId,
				participationId,
				ChatMessageType.USER,
				clientMessageId,
				replyCertificationId,
				content,
				createdAtUtc);
	}

	public static ChatMessage system(Long challengeId, String content, LocalDateTime createdAtUtc) {
		return new ChatMessage(challengeId, null, ChatMessageType.SYSTEM, null, null, content, createdAtUtc);
	}

	public Long getId() { return id; }
	public Long getChallengeId() { return challengeId; }
	public Long getParticipationId() { return participationId; }
	public ChatMessageType getType() { return type; }
	public UUID getClientMessageId() { return clientMessageId; }
	public Long getReplyCertificationId() { return replyCertificationId; }
	public String getContent() { return content; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}
