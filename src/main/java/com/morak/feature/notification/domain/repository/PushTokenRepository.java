package com.morak.feature.notification.domain.repository;

import com.morak.feature.notification.domain.entity.PushChannel;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PushTokenRepository {

	/**
	 * 사용자의 푸시 토큰을 저장한다. 사용자의 구독이 없으면 만들고, 같은 토큰이 이미 있으면(다른 사용자 것이어도)
	 * 지금 사용자의 것으로 덮어쓴다. 같은 요청을 여러 번, 동시에 보내도 토큰은 한 건만 남는다.
	 */
	void register(Long userId, PushChannel channel, String token, String userAgent, LocalDateTime nowUtc);

	/** 사용자 본인의 토큰만 삭제한다. 없거나 남의 토큰이면 지우지 않고 false를 돌려준다. */
	boolean delete(Long userId, String token);

	/** 푸시 수신을 켜 둔 사용자의 해당 채널 토큰 목록. 구독이 없거나 꺼져 있으면 비어 있다. */
	List<String> findEnabledTokens(Long userId, PushChannel channel);

	/** 주인과 상관없이 토큰 값으로 삭제한다. 푸시 서비스가 더 이상 쓸 수 없다고 알려 준 토큰을 정리할 때 쓴다. */
	int deleteByTokens(Collection<String> tokens);
}
