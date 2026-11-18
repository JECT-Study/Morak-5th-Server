package com.morak.feature.ad.infrastructure.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.morak.common.exception.BusinessException;
import com.morak.feature.ad.domain.exception.AdErrorCode;
import com.morak.feature.ad.domain.vo.AdToken;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HmacAdTokenCodecTest {

	private static final AdToken TOKEN = new AdToken(
			"3f2b8c1e-aaaa-bbbb-cccc-1234567890ab", 10L, Instant.parse("2026-11-18T03:00:00Z"), Instant.parse("2026-11-18T03:10:00Z"));

	private final HmacAdTokenCodec codec = new HmacAdTokenCodec("test-secret-test-secret-test-secret");

	@Test
	void issuedTokenCanBeParsedBack() {
		AdToken parsed = codec.parse(codec.issue(TOKEN));

		assertEquals(TOKEN, parsed);
	}

	@Test
	void tamperedPayloadIsRejected() {
		String[] parts = codec.issue(TOKEN).split("\\.");
		// 다른 사용자(11)로 바꾼 내용에 기존 서명을 붙인다.
		String forged = new HmacAdTokenCodec("test-secret-test-secret-test-secret")
				.issue(new AdToken(TOKEN.id(), 11L, TOKEN.issuedAt(), TOKEN.expiresAt())).split("\\.")[0] + "." + parts[1];

		assertInvalid(forged);
	}

	@Test
	void tokenSignedWithAnotherSecretIsRejected() {
		String foreign = new HmacAdTokenCodec("another-secret-another-secret-12345").issue(TOKEN);

		assertInvalid(foreign);
	}

	@Test
	void malformedTokensAreRejected() {
		assertInvalid("");
		assertInvalid("abc");
		assertInvalid("a.b.c");
		assertInvalid("!!!.???");
		assertInvalid(null);
		assertInvalid("x".repeat(600));
	}

	@Test
	void blankSecretMakesCodecUnavailable() {
		HmacAdTokenCodec unconfigured = new HmacAdTokenCodec("");

		BusinessException issue = assertThrows(BusinessException.class, () -> unconfigured.issue(TOKEN));
		BusinessException parse = assertThrows(BusinessException.class, () -> unconfigured.parse("a.b"));

		assertEquals(AdErrorCode.UNAVAILABLE, issue.getErrorCode());
		assertEquals(AdErrorCode.UNAVAILABLE, parse.getErrorCode());
	}

	private void assertInvalid(String raw) {
		BusinessException exception = assertThrows(BusinessException.class, () -> codec.parse(raw));
		assertEquals(AdErrorCode.INVALID_TOKEN, exception.getErrorCode());
	}
}
