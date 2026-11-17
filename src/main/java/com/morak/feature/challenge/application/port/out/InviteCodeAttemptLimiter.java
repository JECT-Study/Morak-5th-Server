package com.morak.feature.challenge.application.port.out;

/** 초대 코드 무차별 대입을 막는 사용자별 실패 횟수 제한(위키 §9 O-24, D-39). */
public interface InviteCodeAttemptLimiter {

	/** 실패가 누적되어 지금 초대 코드 조회를 막아야 하는지. */
	boolean isBlocked(long userId);

	/** 유효하지 않은 코드로 조회한 실패를 한 번 기록한다. */
	void recordFailure(long userId);
}
