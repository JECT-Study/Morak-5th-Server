package com.morak.feature.challenge.domain.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class InviteCodePolicyTest {

	@Test
	void generatedCodesAlwaysMatchFormat() {
		Random random = new Random(1);
		for (int i = 0; i < 1_000; i++) {
			String code = InviteCodePolicy.generate(random);
			assertEquals(InviteCodePolicy.LENGTH, code.length());
			assertTrue(InviteCodePolicy.matchesFormat(code), code);
		}
	}

	@Test
	void padsSmallNumbersWithZeros() {
		Random zero = new Random() {
			@Override
			public int nextInt(int bound) {
				return 42;
			}
		};
		assertEquals("000042", InviteCodePolicy.generate(zero));
	}

	@Test
	void rejectsCodesThatCannotBeIssued() {
		assertFalse(InviteCodePolicy.matchesFormat(null));
		assertFalse(InviteCodePolicy.matchesFormat("12345"));
		assertFalse(InviteCodePolicy.matchesFormat("1234567"));
		assertFalse(InviteCodePolicy.matchesFormat("12a456"));
		assertFalse(InviteCodePolicy.matchesFormat("12345 "));
	}
}
