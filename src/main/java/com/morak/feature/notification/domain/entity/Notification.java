package com.morak.feature.notification.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 앱 안 알림함에 쌓이는 알림 한 건. 만들면 수정하지 않고, 읽음 처리만 read_at을 채운다. */
@Entity
@Table(name = "notification")
public class Notification {

	public static final int MAX_TITLE_LENGTH = 100;
	public static final int MAX_BODY_LENGTH = 500;
	public static final int MAX_TARGET_TYPE_LENGTH = 30;
	public static final int MAX_DEDUP_KEY_LENGTH = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** users 도메인이라 FK 없이 ID만 저장한다. */
	@Column(name = "recipient_user_id", nullable = false, updatable = false)
	private Long recipientUserId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30, updatable = false)
	private NotificationType type;

	@Column(nullable = false, length = MAX_TITLE_LENGTH, updatable = false)
	private String title;

	@Column(nullable = false, length = MAX_BODY_LENGTH, updatable = false)
	private String body;

	/** 알림을 눌렀을 때 이동할 대상 종류. 이동할 곳이 없으면 null. */
	@Column(name = "target_type", length = MAX_TARGET_TYPE_LENGTH, updatable = false)
	private String targetType;

	@Column(name = "target_id", updatable = false)
	private Long targetId;

	/** 같은 알림을 두 번 만들지 않도록 유니크로 막는 키. 키가 없는 알림은 null (유니크 검사에서 제외). */
	@Column(name = "dedup_key", length = MAX_DEDUP_KEY_LENGTH, updatable = false)
	private String dedupKey;

	/** UTC 시각 (백엔드 위키 §3: DATETIME은 UTC). 읽지 않았으면 null. */
	@Column(name = "read_at")
	private LocalDateTime readAt;

	/** UTC 시각. */
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected Notification() {}

	private Notification(
			Long recipientUserId,
			NotificationType type,
			String title,
			String body,
			String targetType,
			Long targetId,
			String dedupKey,
			LocalDateTime createdAt) {
		this.recipientUserId = recipientUserId;
		this.type = type;
		this.title = title;
		this.body = body;
		this.targetType = targetType;
		this.targetId = targetId;
		this.dedupKey = dedupKey;
		this.createdAt = createdAt;
	}

	public static Notification create(
			Long recipientUserId,
			NotificationType type,
			String title,
			String body,
			String targetType,
			Long targetId,
			String dedupKey,
			LocalDateTime createdAtUtc) {
		return new Notification(recipientUserId, type, title, body, targetType, targetId, dedupKey, createdAtUtc);
	}

	public Long getId() { return id; }
	public Long getRecipientUserId() { return recipientUserId; }
	public NotificationType getType() { return type; }
	public String getTitle() { return title; }
	public String getBody() { return body; }
	public String getTargetType() { return targetType; }
	public Long getTargetId() { return targetId; }
	public String getDedupKey() { return dedupKey; }
	public LocalDateTime getReadAt() { return readAt; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}
