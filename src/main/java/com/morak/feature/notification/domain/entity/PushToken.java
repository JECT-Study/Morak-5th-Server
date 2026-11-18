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

/** 기기별 푸시 토큰. 토큰 값은 유니크라서, 같은 기기에 다른 사용자가 로그인하면 소유자만 바뀐다. */
@Entity
@Table(name = "push_token")
public class PushToken {

	public static final int MAX_TOKEN_LENGTH = 700;
	public static final int MAX_USER_AGENT_LENGTH = 255;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "subscription_id", nullable = false)
	private Long subscriptionId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private PushChannel channel;

	@Column(nullable = false, unique = true, length = MAX_TOKEN_LENGTH)
	private String token;

	/** WEB 전용. 지금은 쓰지 않는다. */
	@Column(length = 255)
	private String p256dh;

	/** WEB 전용. 지금은 쓰지 않는다. */
	@Column(length = 255)
	private String auth;

	@Column(name = "user_agent", length = MAX_USER_AGENT_LENGTH)
	private String userAgent;

	/** UTC 시각. 발송에 성공한 적이 없으면 null. */
	@Column(name = "last_success_at")
	private LocalDateTime lastSuccessAt;

	/** UTC 시각. */
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected PushToken() {}

	public Long getId() { return id; }
	public Long getSubscriptionId() { return subscriptionId; }
	public PushChannel getChannel() { return channel; }
	public String getToken() { return token; }
	public String getUserAgent() { return userAgent; }
	public LocalDateTime getLastSuccessAt() { return lastSuccessAt; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}
