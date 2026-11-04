package com.morak.feature.user.domain.vo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserIdTest {
	@Test
	void rejectsMissingAndNonPositiveIds() {
		assertThrows(IllegalArgumentException.class, () -> new UserId(null));
		assertThrows(IllegalArgumentException.class, () -> new UserId(0L));
		assertThrows(IllegalArgumentException.class, () -> new UserId(-1L));
	}

	@Test
	void comparesByValue() {
		assertEquals(new UserId(7L), new UserId(7L));
		assertNotEquals(new UserId(7L), new UserId(8L));
	}
}
