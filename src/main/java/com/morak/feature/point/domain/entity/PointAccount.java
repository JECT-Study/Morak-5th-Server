package com.morak.feature.point.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 유저당 1개인 포인트 계정. 잔액은 동시성 때문에 엔티티가 아니라 조건부 UPDATE로만 바꾼다. */
@Entity
@Table(name = "point_account")
public class PointAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** user 도메인이라 FK 없이 ID만 저장한다. (user_id)가 유니크다. */
	@Column(name = "user_id", nullable = false, unique = true)
	private Long userId;

	/** 원 단위. DB에서 CHECK (balance >= 0)로 음수를 막는다. */
	@Column(nullable = false)
	private Long balance;

	/** UTC 시각 (백엔드 위키 §3: DATETIME은 UTC). */
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	/** 잔액 변경 시각 (UTC). */
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected PointAccount() {}

	private PointAccount(Long userId, LocalDateTime createdAt) {
		this.userId = userId;
		this.balance = 0L;
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
	}

	/** 잔액 0으로 계정을 연다. */
	public static PointAccount open(Long userId, LocalDateTime createdAtUtc) {
		return new PointAccount(userId, createdAtUtc);
	}

	public Long getId() { return id; }
	public Long getUserId() { return userId; }
	public Long getBalance() { return balance; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
}
