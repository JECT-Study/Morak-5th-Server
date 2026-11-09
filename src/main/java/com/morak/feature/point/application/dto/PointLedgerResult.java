package com.morak.feature.point.application.dto;

import com.morak.feature.point.domain.entity.PointGrantReason;
import com.morak.feature.point.domain.entity.PointTransaction;
import com.morak.feature.point.domain.entity.PointTransactionType;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * @param transactions 최신순
 * @param nextCursor 다음 페이지 요청 시 before로 넘길 값. 마지막 페이지면 null
 */
public record PointLedgerResult(long balance, List<Entry> transactions, Long nextCursor) {

	public static PointLedgerResult of(long balance, List<PointTransaction> transactions, Long nextCursor) {
		return new PointLedgerResult(balance, transactions.stream().map(Entry::from).toList(), nextCursor);
	}

	public static PointLedgerResult empty() {
		return new PointLedgerResult(0, List.of(), null);
	}

	/**
	 * @param reason GRANT일 때만 있다
	 * @param challengeTitle 보증금 거래일 때만 있다
	 * @param amount 입금은 +, 출금은 -
	 */
	public record Entry(
			Long id,
			PointTransactionType type,
			PointGrantReason reason,
			String challengeTitle,
			long amount,
			long balanceAfter,
			Instant createdAt) {

		static Entry from(PointTransaction transaction) {
			return new Entry(
					transaction.getId(),
					transaction.getType(),
					transaction.getReason(),
					transaction.getChallengeTitle(),
					transaction.getAmount(),
					transaction.getBalanceAfter(),
					transaction.getCreatedAt().toInstant(ZoneOffset.UTC));
		}
	}
}
