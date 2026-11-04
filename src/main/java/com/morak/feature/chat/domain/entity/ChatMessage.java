package com.morak.feature.chat.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_message")
public class ChatMessage {

	public static final int MAX_CONTENT_LENGTH = 1000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "challenge_id", nullable = false)
	private Long challengeId;

	@Column(name = "participation_id", nullable = false)
	private Long participationId;

	@Column(nullable = false, length = MAX_CONTENT_LENGTH)
	private String content;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	protected ChatMessage() {}

	public ChatMessage(Long challengeId, Long participationId, String content, LocalDateTime createdAt) {
		this.challengeId = challengeId;
		this.participationId = participationId;
		this.content = content;
		this.createdAt = createdAt;
	}

	public boolean isDeleted() { return deletedAt != null; }

	public Long getId() { return id; }
	public Long getChallengeId() { return challengeId; }
	public Long getParticipationId() { return participationId; }
	public String getContent() { return content; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public LocalDateTime getDeletedAt() { return deletedAt; }
}
