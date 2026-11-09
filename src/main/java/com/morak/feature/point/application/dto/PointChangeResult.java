package com.morak.feature.point.application.dto;

import com.morak.feature.point.domain.entity.PointTransaction;

/** @param amount 변동 금액. 입금은 +, 출금은 - */
public record PointChangeResult(Long transactionId, long amount, long balanceAfter) {

	public static PointChangeResult from(PointTransaction transaction) {
		return new PointChangeResult(transaction.getId(), transaction.getAmount(), transaction.getBalanceAfter());
	}
}
