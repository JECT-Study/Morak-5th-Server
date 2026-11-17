package com.morak.feature.challenge.infrastructure.ratelimit;

import com.morak.feature.challenge.application.port.out.InviteCodeAttemptLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 서버 메모리 초대 코드 실패 횟수 제한.
 * 첫 실패부터 10분 안에 10번 틀리면 그때부터 10분 동안 조회를 막는다. 맞는 코드로 조회해도 횟수는 줄지 않는다.
 * 채팅 티켓 저장소처럼 서버 1대 전제다. 서버를 여러 대로 늘릴 때는 Redis 구현체(INCR + EXPIRE)로 교체한다.
 */
// TODO(D-39): 실패 10회 / 10분 창 / 10분 차단은 PM 확인 전 제안값이다.
@Component
public class InMemoryInviteCodeAttemptLimiter implements InviteCodeAttemptLimiter {

	static final int MAX_FAILURES = 10;
	static final Duration WINDOW = Duration.ofMinutes(10);
	static final Duration BLOCK = Duration.ofMinutes(10);

	private final Map<Long, Attempts> attempts = new ConcurrentHashMap<>();
	private final Clock clock;

	public InMemoryInviteCodeAttemptLimiter(Clock clock) {
		this.clock = clock;
	}

	@Override
	public boolean isBlocked(long userId) {
		Attempts current = attempts.get(userId);
		return current != null && current.isBlocked(clock.instant());
	}

	@Override
	public void recordFailure(long userId) {
		Instant now = clock.instant();
		removeExpired(now);
		// compute는 키 단위로 원자적이라 같은 사용자의 동시 실패도 빠짐없이 센다.
		attempts.compute(userId, (id, current) -> {
			if (current == null || current.isExpired(now)) {
				return Attempts.first(now);
			}
			return current.isBlocked(now) ? current : current.plusFailure(now);
		});
	}

	/** 창과 차단이 모두 끝난 기록 정리. 실패 기록 때마다 수행해 별도 스케줄러 없이 메모리를 유지한다. */
	private void removeExpired(Instant now) {
		attempts.values().removeIf(entry -> entry.isExpired(now));
	}

	private record Attempts(int failures, Instant windowStart, Instant blockedUntil) {

		static Attempts first(Instant now) {
			return new Attempts(1, now, null);
		}

		Attempts plusFailure(Instant now) {
			int next = failures + 1;
			return new Attempts(next, windowStart, next >= MAX_FAILURES ? now.plus(BLOCK) : null);
		}

		boolean isBlocked(Instant now) {
			return blockedUntil != null && now.isBefore(blockedUntil);
		}

		boolean isExpired(Instant now) {
			return !isBlocked(now) && !now.isBefore(windowStart.plus(WINDOW));
		}
	}
}
