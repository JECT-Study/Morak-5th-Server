package com.morak.feature.notification.domain.repository;

import com.morak.feature.notification.domain.entity.PushChannel;
import java.time.LocalDateTime;

public interface PushTokenRepository {

	/**
	 * 사용자의 푸시 토큰을 저장한다. 사용자의 구독이 없으면 만들고, 같은 토큰이 이미 있으면(다른 사용자 것이어도)
	 * 지금 사용자의 것으로 덮어쓴다. 같은 요청을 여러 번, 동시에 보내도 토큰은 한 건만 남는다.
	 */
	void register(Long userId, PushChannel channel, String token, String userAgent, LocalDateTime nowUtc);

	/** 사용자 본인의 토큰만 삭제한다. 없거나 남의 토큰이면 지우지 않고 false를 돌려준다. */
	boolean delete(Long userId, String token);
}
