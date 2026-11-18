package com.morak.feature.notification.infrastructure.persistence;

import com.morak.feature.notification.domain.entity.PushChannel;
import com.morak.feature.notification.domain.repository.PushTokenRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class PushTokenRepositoryAdapter implements PushTokenRepository {

	private final JpaPushSubscriptionRepository subscriptions;
	private final JpaPushTokenRepository tokens;

	public PushTokenRepositoryAdapter(JpaPushSubscriptionRepository subscriptions, JpaPushTokenRepository tokens) {
		this.subscriptions = subscriptions;
		this.tokens = tokens;
	}

	@Override
	public void register(Long userId, PushChannel channel, String token, String userAgent, LocalDateTime nowUtc) {
		subscriptions.insertIfAbsent(userId, nowUtc);
		Long subscriptionId = subscriptions.findByUserId(userId).orElseThrow().getId();
		tokens.upsert(subscriptionId, channel.name(), token, userAgent, nowUtc);
	}

	@Override
	public boolean delete(Long userId, String token) {
		return tokens.deleteOwnedToken(userId, token) > 0;
	}

	@Override
	public List<String> findEnabledTokens(Long userId, PushChannel channel) {
		return tokens.findEnabledTokens(userId, channel);
	}

	@Override
	public int deleteByTokens(Collection<String> tokenValues) {
		return tokenValues.isEmpty() ? 0 : tokens.deleteByTokens(tokenValues);
	}
}
