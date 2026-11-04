package com.morak.feature.chat.infrastructure.ticket;

import com.morak.feature.chat.application.port.out.ChatTicketStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 서버 메모리 티켓 저장소.
 * SimpleBroker를 쓰는 동안 채팅은 서버 1대 전제이므로 메모리로 충분하다.
 * 서버를 여러 대로 늘릴 때는 Redis 구현체(SET EX + GETDEL)로 교체한다.
 */
@Component
public class InMemoryChatTicketStore implements ChatTicketStore {

	private final Map<String, Entry> tickets = new ConcurrentHashMap<>();
	private final Clock clock;

	public InMemoryChatTicketStore(Clock clock) {
		this.clock = clock;
	}

	@Override
	public void save(String ticket, Long userId, Instant expiresAt) {
		removeExpired();
		tickets.put(ticket, new Entry(userId, expiresAt));
	}

	@Override
	public Optional<Long> consume(String ticket) {
		// remove는 원자적이라 같은 티켓으로 동시에 들어와도 한 쪽만 꺼낸다.
		Entry entry = tickets.remove(ticket);
		if (entry == null || entry.isExpired(clock.instant())) {
			return Optional.empty();
		}
		return Optional.of(entry.userId());
	}

	/** 사용되지 않고 남은 티켓 정리. 발급 때마다 수행해 별도 스케줄러 없이 메모리를 유지한다. */
	private void removeExpired() {
		Instant now = clock.instant();
		tickets.values().removeIf(entry -> entry.isExpired(now));
	}

	private record Entry(Long userId, Instant expiresAt) {

		boolean isExpired(Instant now) {
			return !now.isBefore(expiresAt);
		}
	}
}
