package com.morak.feature.auth.infrastructure.token;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServiceTokenProviderTest {

	private static final String SECRET = "01234567890123456789012345678901";

	@Test
	void rejectsValidityBelowOneSecond() {
		for (Duration validity : new Duration[] {Duration.ZERO, Duration.ofSeconds(-1), Duration.ofMillis(999)}) {
			assertThrows(IllegalStateException.class, () -> new ServiceTokenProvider(SECRET, validity));
		}
	}

	@Test
	void acceptsOneSecondAndPreservesWholeSeconds() {
		assertEquals(1, new ServiceTokenProvider(SECRET, Duration.ofSeconds(1)).accessValiditySeconds());
		assertEquals(60, new ServiceTokenProvider(SECRET, Duration.ofMillis(60999)).accessValiditySeconds());
	}
}
