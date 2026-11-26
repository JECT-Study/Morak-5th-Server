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
	void generatesFromDigitsAndUppercaseLetters() {
		assertEquals("000000", InviteCodePolicy.generate(fixed(0)));
		assertEquals("999999", InviteCodePolicy.generate(fixed(9)));
		assertEquals("AAAAAA", InviteCodePolicy.generate(fixed(10)));
		assertEquals("ZZZZZZ", InviteCodePolicy.generate(fixed(35)));
	}

	@Test
	void acceptsUppercaseAlphanumericAndExistingNumericCodes() {
		assertTrue(InviteCodePolicy.matchesFormat("AB12CZ"));
		assertTrue(InviteCodePolicy.matchesFormat("004213"));
	}

	@Test
	void rejectsCodesThatCannotBeIssued() {
		assertFalse(InviteCodePolicy.matchesFormat(null));
		assertFalse(InviteCodePolicy.matchesFormat("12345"));
		assertFalse(InviteCodePolicy.matchesFormat("1234567"));
		assertFalse(InviteCodePolicy.matchesFormat("12a456"));
		assertFalse(InviteCodePolicy.matchesFormat("ab12cz"));
		assertFalse(InviteCodePolicy.matchesFormat("12345 "));
		assertFalse(InviteCodePolicy.matchesFormat("12-456"));
	}

	private static Random fixed(int value) {
		return new Random() {
			@Override
			public int nextInt(int bound) {
				assertEquals(36, bound);
				return value;
			}
		};
	}
}
