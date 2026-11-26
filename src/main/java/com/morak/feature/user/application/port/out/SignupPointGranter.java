package com.morak.feature.user.application.port.out;

/** 온보딩 완료 시 가입 포인트를 지급한다. 다른 도메인(point)은 이 포트로만 호출한다. 위키 §4.1, §4.6. */
public interface SignupPointGranter {

	/** 호출자의 트랜잭션 안에서 지급한다. 같은 회원에게 다시 호출해도 한 번만 반영된다(키 SIGNUP:{userId}). */
	void grant(long userId);
}
