package com.morak.feature.notification.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 사용자별 푸시 수신 설정. 사용자당 한 행이고, 기기별 토큰(PushToken)이 여기에 딸린다. */
@Entity
@Table(name = "push_subscription")
public class PushSubscription {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** users 도메인이라 FK 없이 ID만 저장한다. */
	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(nullable = false)
	private boolean enabled;

	/** UTC 시각. */
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected PushSubscription() {}

	public Long getId() { return id; }
	public Long getUserId() { return userId; }
	public boolean isEnabled() { return enabled; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
}
