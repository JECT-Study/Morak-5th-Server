package com.morak.feature.challenge.application.port.out;

import java.time.Duration;

/**
 * 초대 코드 무차별 대입을 막는 사용자별 실패 횟수 제한(위키 §9 O-24, D-40).
 * 조회 전에 {@link #tryAcquire}로 시도 한 칸을 선점하고, 조회가 끝나면 {@link #release}로 결과를 알린다.
 * 선점한 시도도 한도에 포함하므로 동시에 몰린 요청이 한도를 넘어 조회되지 않는다.
 */
public interface InviteCodeAttemptLimiter {

	/** 조회 한 번을 선점한다. 거절되면 다시 시도할 수 있을 때까지 남은 시간을 함께 돌려준다. */
	Decision tryAcquire(long userId);

	/** 선점한 조회를 끝낸다. 유효하지 않은 코드였으면 {@code failed}로 실패를 기록한다. */
	void release(long userId, boolean failed);

	record Decision(boolean allowed, Duration retryAfter) {

		public static Decision allow() {
			return new Decision(true, Duration.ZERO);
		}

		public static Decision deny(Duration retryAfter) {
			return new Decision(false, retryAfter);
		}
	}
}
