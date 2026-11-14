package com.morak.feature.point.presentation.dto.response;

import com.morak.feature.point.application.dto.PointLedgerResult;
import com.morak.feature.point.domain.entity.PointGrantReason;
import com.morak.feature.point.domain.entity.PointTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record PointLedgerResponse(
		@Schema(description = "현재 잔액 (P)", example = "7000")
		long balance,
		@Schema(description = "거래 내역 (최신순)")
		List<TransactionResponse> transactions,
		@Schema(description = "다음 페이지 요청 시 before로 넘길 값. 마지막 페이지면 null", nullable = true, example = "31")
		Long nextCursor) {

	public static PointLedgerResponse from(PointLedgerResult result) {
		return new PointLedgerResponse(
				result.balance(),
				result.transactions().stream().map(TransactionResponse::from).toList(),
				result.nextCursor());
	}

	public record TransactionResponse(
			@Schema(description = "거래 ID", example = "42")
			Long id,
			@Schema(description = "GRANT: 포인트 지급, DEPOSIT: 챌린지 보증금으로 포인트 차감, REFUND: 챌린지 보증금 포인트 반환")
			PointTransactionType type,
			@Schema(description = "지급 사유. GRANT일 때만 있다", nullable = true)
			PointGrantReason reason,
			@Schema(description = "거래 당시 챌린지 이름. 보증금 거래일 때만 있다", nullable = true, example = "매일 아침 러닝")
			String challengeTitle,
			@Schema(description = "변동 포인트 (P). 지급·반환은 +, 차감은 -", example = "-3000")
			long amount,
			@Schema(description = "거래 후 잔액 (P)", example = "7000")
			long balanceAfter,
			@Schema(description = "거래 시각 (UTC, 화면에는 KST로 변환)", example = "2026-11-09T03:00:00Z")
			Instant createdAt) {

		static TransactionResponse from(PointLedgerResult.Entry entry) {
			return new TransactionResponse(
					entry.id(),
					entry.type(),
					entry.reason(),
					entry.challengeTitle(),
					entry.amount(),
					entry.balanceAfter(),
					entry.createdAt());
		}
	}
}
