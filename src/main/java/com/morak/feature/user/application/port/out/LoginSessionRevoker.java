package com.morak.feature.user.application.port.out;

/** 탈퇴 시 해당 회원의 로그인 세션을 모두 끊는다. auth 도메인은 이 포트로만 호출한다. */
public interface LoginSessionRevoker {

	void revokeAll(long userId);
}
