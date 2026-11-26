package com.morak.feature.participation.domain.policy;

import java.util.OptionalInt;

/** 개인 인증률(위키 §4.1). 챌린지별 평균이 아니라 누적 승인 ÷ 누적 의무 횟수다. */
public final class CertificationRate {

	private CertificationRate() {
	}

	/** 정수 퍼센트. 소수 첫째 자리에서 half-up으로 반올림한다. 의무가 0이면(인증 기록 없음) empty. */
	public static OptionalInt percent(int approvedCount, int obligationCount) {
		if (obligationCount <= 0) {
			return OptionalInt.empty();
		}
		long numerator = 200L * approvedCount + obligationCount;
		return OptionalInt.of((int) (numerator / (2L * obligationCount)));
	}
}
