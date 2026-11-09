package com.morak.feature.point.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 포인트 거래 원장. 추가만 하고 수정·삭제하지 않는다. */
@Entity
@Table(name = "point_transaction")
public class PointTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "account_id", nullable = false, updatable = false)
	private Long accountId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private PointTransactionType type;

	/** GRANT일 때만 값이 있다. */
	@Enumerated(EnumType.STRING)
	@Column(length = 30, updatable = false)
	private PointGrantReason reason;

	/** 변동 금액. 입금은 +, 출금은 -이고 0은 DB CHECK로 막는다. */
	@Column(nullable = false, updatable = false)
	private Long amount;

	@Column(name = "balance_after", nullable = false, updatable = false)
	private Long balanceAfter;

	/** 보증금 거래의 참가자 ID. participation 도메인이라 FK 없이 ID만 저장한다. */
	@Column(name = "participation_id", updatable = false)
	private Long participationId;

	/** 내역 표시용 거래 당시 챌린지 이름. 보증금 거래에만 있고, 이후 이름이 바뀌어도 갱신하지 않는다. */
	@Column(name = "challenge_title", length = 50, updatable = false)
	private String challengeTitle;

	/** 같은 거래를 두 번 처리하지 않도록 유니크로 막는 키 (예: DEPOSIT:{participationId}). */
	@Column(name = "idempotency_key", nullable = false, unique = true, length = 100, updatable = false)
	private String idempotencyKey;

	/** UTC 시각 (백엔드 위키 §3: DATETIME은 UTC). */
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected PointTransaction() {}

	private PointTransaction(
			Long accountId,
			PointTransactionType type,
			PointGrantReason reason,
			Long amount,
			Long balanceAfter,
			Long participationId,
			String challengeTitle,
			String idempotencyKey,
			LocalDateTime createdAt) {
		this.accountId = accountId;
		this.type = type;
		this.reason = reason;
		this.amount = amount;
		this.balanceAfter = balanceAfter;
		this.participationId = participationId;
		this.challengeTitle = challengeTitle;
		this.idempotencyKey = idempotencyKey;
		this.createdAt = createdAt;
	}

	/** 지급. amount는 양수로 받는다. */
	public static PointTransaction grant(
			Long accountId,
			PointGrantReason reason,
			long amount,
			long balanceAfter,
			String idempotencyKey,
			LocalDateTime createdAtUtc) {
		return new PointTransaction(
				accountId,
				PointTransactionType.GRANT,
				reason,
				amount,
				balanceAfter,
				null,
				null,
				idempotencyKey,
				createdAtUtc);
	}

	/** 보증금 납부. amount는 양수로 받고 출금이라 음수로 저장한다. */
	public static PointTransaction deposit(
			Long accountId,
			Long participationId,
			String challengeTitle,
			long amount,
			long balanceAfter,
			LocalDateTime createdAtUtc) {
		return new PointTransaction(
				accountId,
				PointTransactionType.DEPOSIT,
				null,
				-amount,
				balanceAfter,
				participationId,
				challengeTitle,
				"DEPOSIT:" + participationId,
				createdAtUtc);
	}

	/** 보증금 환급. amount는 양수로 받는다. */
	public static PointTransaction refund(
			Long accountId,
			Long participationId,
			String challengeTitle,
			long amount,
			long balanceAfter,
			LocalDateTime createdAtUtc) {
		return new PointTransaction(
				accountId,
				PointTransactionType.REFUND,
				null,
				amount,
				balanceAfter,
				participationId,
				challengeTitle,
				"REFUND:" + participationId,
				createdAtUtc);
	}

	public Long getId() { return id; }
	public Long getAccountId() { return accountId; }
	public PointTransactionType getType() { return type; }
	public PointGrantReason getReason() { return reason; }
	public Long getAmount() { return amount; }
	public Long getBalanceAfter() { return balanceAfter; }
	public Long getParticipationId() { return participationId; }
	public String getChallengeTitle() { return challengeTitle; }
	public String getIdempotencyKey() { return idempotencyKey; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}
