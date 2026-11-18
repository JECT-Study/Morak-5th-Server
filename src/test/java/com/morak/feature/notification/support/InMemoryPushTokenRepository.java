package com.morak.feature.notification.support;

import com.morak.feature.notification.domain.entity.PushChannel;
import com.morak.feature.notification.domain.repository.PushTokenRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 서비스 단위 테스트용 저장소. 토큰이 유니크이고 덮어쓰기로 소유자가 바뀌는 것을 흉내 낸다. */
public class InMemoryPushTokenRepository implements PushTokenRepository {

	public record Stored(Long userId, PushChannel channel, String userAgent, LocalDateTime registeredAt) {}

	public final Map<String, Stored> tokens = new LinkedHashMap<>();

	@Override
	public void register(Long userId, PushChannel channel, String token, String userAgent, LocalDateTime nowUtc) {
		tokens.put(token, new Stored(userId, channel, userAgent, nowUtc));
	}

	@Override
	public boolean delete(Long userId, String token) {
		Stored stored = tokens.get(token);
		if (stored == null || !stored.userId().equals(userId)) {
			return false;
		}
		tokens.remove(token);
		return true;
	}
}
