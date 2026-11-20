package com.morak.feature.participation.domain.entity;

import java.math.BigDecimal;

/** 개인 인증률(%) 계산. 정수 연산만 쓰고 소수 첫째 자리에서 half-up 반올림한다 (위키 §3, §4.5). */
final class AchievementRate {

	private static final int SCALE = 2;

	private AchievementRate() {}

	/**
	 * 승인 수 ÷ 인증 대상 수를 퍼센트로 바꾼다. 결과는 소수 첫째 자리까지 의미가 있고 둘째 자리는 항상 0이다.
	 * 대상 수가 0이면 0.00이다. "인증 기록 없음" 표시는 화면이 totalCount로 판단한다.
	 * TODO(D-28): 컬럼이 NUMERIC(5,2)지만 위키 규칙은 소수 첫째 자리 반올림이라 둘째 자리를 0으로 저장한다.
	 */
	static BigDecimal percent(int approvedCount, int totalCount) {
		if (totalCount == 0) {
			return BigDecimal.ZERO.setScale(SCALE);
		}
		// 천분율(소수 첫째 자리 단위): (2*승인*1000 + 대상) / (2*대상) 이 half-up이다.
		long tenths = (2L * approvedCount * 1000 + totalCount) / (2L * totalCount);
		return BigDecimal.valueOf(tenths, 1).setScale(SCALE);
	}
}
