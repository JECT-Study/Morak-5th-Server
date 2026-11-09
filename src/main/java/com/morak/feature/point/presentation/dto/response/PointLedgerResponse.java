package com.morak.feature.point.presentation.dto.response;

import com.morak.feature.point.application.dto.PointLedgerResult;
import com.morak.feature.point.domain.entity.PointGrantReason;
import com.morak.feature.point.domain.entity.PointTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record PointLedgerResponse(
		@Schema(description = "현재 잔액 (원)", example = "7000")
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
			@Schema(description = "GRANT: 지급, DEPOSIT: 보증금 납부, REFUND: 보증금 환급")
			PointTransactionType type,
			@Schema(description = "지급 사유. GRANT일 때만 있다", nullable = true)
			PointGrantReason reason,
			@Schema(description = "거래 당시 챌린지 이름. 보증금 거래일 때만 있다", nullable = true, example = "매일 아침 러닝")
			String challengeTitle,
			@Schema(description = "변동 금액. 입금은 +, 출금은 -", example = "-3000")
			long amount,
			@Schema(description = "거래 후 잔액", example = "7000")
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
