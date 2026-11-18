package com.morak.feature.notification.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PushLinksTest {

	@Test
	void challengeTargetBecomesPathWithoutDomain() {
		assertEquals("/challenges/123", PushLinks.pathOf("CHALLENGE", 123L));
	}

	@Test
	void missingTargetHasNoPath() {
		assertNull(PushLinks.pathOf(null, null));
		assertNull(PushLinks.pathOf("CHALLENGE", null));
		assertNull(PushLinks.pathOf(null, 123L));
	}

	@Test
	void unknownTargetTypeHasNoPathYet() {
		assertNull(PushLinks.pathOf("PARTICIPATION", 1L));
	}
}
