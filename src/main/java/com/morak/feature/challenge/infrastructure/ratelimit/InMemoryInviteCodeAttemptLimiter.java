package com.morak.feature.challenge.infrastructure.ratelimit;

import com.morak.feature.challenge.application.port.out.InviteCodeAttemptLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 서버 메모리 초대 코드 실패 횟수 제한.
 * 첫 실패부터 10분 안에 10번 틀리면 그때부터 10분 동안 조회를 막는다. 맞는 코드로 조회해도 횟수는 줄지 않는다.
 * 실패 횟수와 진행 중인 조회 수의 합이 한도에 닿으면 새 조회를 받지 않아, 동시에 몰린 요청도 한도를 넘지 못한다.
 * 채팅 티켓 저장소처럼 서버 1대 전제다. 서버를 여러 대로 늘릴 때는 Redis 구현체로 교체한다.
 */
// TODO(D-40): 실패 10회 / 10분 창 / 10분 차단은 PM 확인 전 제안값이다.
@Component
public class InMemoryInviteCodeAttemptLimiter implements InviteCodeAttemptLimiter {

	static final int MAX_FAILURES = 10;
	static final Duration WINDOW = Duration.ofMinutes(10);
	static final Duration BLOCK = Duration.ofMinutes(10);
	/** 진행 중인 조회 때문에 잠시 거절할 때 안내하는 재시도 시간. 조회 한 번은 금방 끝난다. */
	static final Duration IN_FLIGHT_RETRY = Duration.ofSeconds(1);
	private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(1);

	private static final Logger log = LoggerFactory.getLogger(InMemoryInviteCodeAttemptLimiter.class);

	private final Map<Long, Attempts> attempts = new ConcurrentHashMap<>();
	private final AtomicReference<Instant> lastCleanup;
	private final Clock clock;

	public InMemoryInviteCodeAttemptLimiter(Clock clock) {
		this.clock = clock;
		this.lastCleanup = new AtomicReference<>(clock.instant());
	}

	@Override
	public Decision tryAcquire(long userId) {
		Instant now = clock.instant();
		cleanUpOccasionally(now);
		Decision[] decision = new Decision[1];
		// compute는 키 단위로 원자적이라 같은 사용자의 동시 요청도 한 칸씩 차례로 선점한다.
		attempts.compute(userId, (id, current) -> {
			Attempts state = current == null ? Attempts.EMPTY : current.refreshed(now);
			if (state.isBlocked(now)) {
				decision[0] = Decision.deny(Duration.between(now, state.blockedUntil()));
				return state;
			}
			if (state.failures() + state.inFlight() >= MAX_FAILURES) {
				decision[0] = Decision.deny(IN_FLIGHT_RETRY);
				return state;
			}
			decision[0] = Decision.allow();
			return state.withInFlight(state.inFlight() + 1);
		});
		return decision[0];
	}

	@Override
	public void release(long userId, boolean failed) {
		Instant now = clock.instant();
		boolean[] blockedNow = new boolean[1];
		attempts.compute(userId, (id, current) -> {
			if (current == null) {
				return null;
			}
			Attempts state = current.refreshed(now).withInFlight(Math.max(0, current.inFlight() - 1));
			if (failed && !state.isBlocked(now)) {
				state = state.plusFailure(now);
				blockedNow[0] = state.isBlocked(now);
			}
			return state.isIdle(now) ? null : state;
		});
		if (blockedNow[0]) {
			log.warn("Invite code lookups blocked userId={} for={}", userId, BLOCK);
		}
	}

	/** 오래 쓰이지 않은 기록 정리. 별도 스케줄러 없이 1분에 한 번만 전체를 훑는다. */
	private void cleanUpOccasionally(Instant now) {
		Instant last = lastCleanup.get();
		if (now.isBefore(last.plus(CLEANUP_INTERVAL)) || !lastCleanup.compareAndSet(last, now)) {
			return;
		}
		attempts.values().removeIf(entry -> entry.refreshed(now).isIdle(now));
	}

	/**
	 * @param windowStart 첫 실패 시각. 실패가 없으면 null.
	 * @param blockedUntil 차단이 풀리는 시각. 차단 전이면 null.
	 */
	private record Attempts(int failures, int inFlight, Instant windowStart, Instant blockedUntil) {

		static final Attempts EMPTY = new Attempts(0, 0, null, null);

		/** 차단이 풀렸거나 창이 지났으면 실패 횟수를 비운다. 진행 중인 조회 수는 유지한다. */
		Attempts refreshed(Instant now) {
			boolean blockEnded = blockedUntil != null && !now.isBefore(blockedUntil);
			boolean windowEnded = blockedUntil == null && windowStart != null && !now.isBefore(windowStart.plus(WINDOW));
			return blockEnded || windowEnded ? new Attempts(0, inFlight, null, null) : this;
		}

		Attempts withInFlight(int value) {
			return new Attempts(failures, value, windowStart, blockedUntil);
		}

		Attempts plusFailure(Instant now) {
			int next = failures + 1;
			return new Attempts(next, inFlight, windowStart == null ? now : windowStart,
					next >= MAX_FAILURES ? now.plus(BLOCK) : null);
		}

		boolean isBlocked(Instant now) {
			return blockedUntil != null && now.isBefore(blockedUntil);
		}

		boolean isIdle(Instant now) {
			return failures == 0 && inFlight == 0 && !isBlocked(now);
		}
	}
}
