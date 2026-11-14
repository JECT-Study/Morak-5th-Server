package com.morak.feature.point.domain.entity;

public enum PointTransactionType {
	/** 지급 (입금). 사유 코드가 함께 저장된다. */
	GRANT,
	/** 챌린지 보증금 납부 (출금) */
	DEPOSIT,
	/** 챌린지 보증금 환급 (입금) */
	REFUND
}
