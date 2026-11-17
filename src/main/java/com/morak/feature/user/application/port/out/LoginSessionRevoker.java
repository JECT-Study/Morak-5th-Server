package com.morak.feature.user.application.port.out;

import java.time.LocalDateTime;

/** 탈퇴 시 해당 회원의 로그인 세션을 모두 끊는다. auth 도메인은 이 포트로만 호출한다. */
public interface LoginSessionRevoker {

	/** now는 탈퇴 시각과 같은 값을 넘겨 폐기 시각이 어긋나지 않게 한다. */
	void revokeAll(long userId, LocalDateTime now);
}
