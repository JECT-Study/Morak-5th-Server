package com.morak.feature.ad.infrastructure.ssv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.common.exception.BusinessException;
import com.morak.feature.ad.domain.exception.AdErrorCode;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 실제 HTTP 서버를 띄워 구글 키 목록(keyId가 숫자인 JSON)을 읽고 기억하는 동작을 확인한다. */
class GoogleVerifierKeysTest {

	private final AtomicInteger requests = new AtomicInteger();
	private volatile int status = 200;
	private PublicKey googleKey;
	private HttpServer server;
	private MutableClock clock;
	private GoogleVerifierKeys keys;

	@BeforeEach
	void setUp() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
		generator.initialize(new ECGenParameterSpec("secp256r1"));
		KeyPair pair = generator.generateKeyPair();
		googleKey = pair.getPublic();
		String base64 = Base64.getEncoder().encodeToString(googleKey.getEncoded());
		// 구글 실제 응답처럼 keyId는 숫자이고, 우리가 쓰지 않는 pem 같은 필드도 들어 있다.
		String json = "{\"keys\":[{\"keyId\":3335741209,\"pem\":\"-----BEGIN PUBLIC KEY-----\\n...\","
				+ "\"base64\":\"" + base64 + "\"}]}";

		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/keys", exchange -> {
			requests.incrementAndGet();
			byte[] body = json.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, status == 200 ? body.length : -1);
			if (status == 200) {
				exchange.getResponseBody().write(body);
			}
			exchange.close();
		});
		server.start();
		clock = new MutableClock(Instant.parse("2026-11-28T03:00:00Z"));
		keys = new GoogleVerifierKeys("http://127.0.0.1:" + server.getAddress().getPort() + "/keys", clock);
	}

	@AfterEach
	void tearDown() {
		server.stop(0);
	}

	@Test
	void readsNumericKeyIdAndParsesPublicKey() {
		Optional<PublicKey> found = keys.find("3335741209");

		assertTrue(found.isPresent());
		assertArrayEquals(googleKey.getEncoded(), found.get().getEncoded());
	}

	@Test
	void knownKeyIsServedFromMemory() {
		keys.find("3335741209");
		keys.find("3335741209");

		assertEquals(1, requests.get());
	}

	@Test
	void unknownKeyDoesNotRefetchWithinAMinuteButDoesAfterwards() {
		assertTrue(keys.find("1").isEmpty());
		assertTrue(keys.find("2").isEmpty());
		assertEquals(1, requests.get());

		clock.advance(Duration.ofMinutes(1));
		assertTrue(keys.find("3").isEmpty());
		assertEquals(2, requests.get());
	}

	@Test
	void fetchFailureIsUnavailableAndNotRetriedWithinAMinute() {
		status = 500;

		assertUnavailable("3335741209");
		assertUnavailable("3335741209");
		assertEquals(1, requests.get());

		status = 200;
		clock.advance(Duration.ofMinutes(1));
		assertTrue(keys.find("3335741209").isPresent());
	}

	private void assertUnavailable(String keyId) {
		BusinessException exception = assertThrows(BusinessException.class, () -> keys.find(keyId));
		assertEquals(AdErrorCode.UNAVAILABLE, exception.getErrorCode());
	}

	private static class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) { this.now = now; }

		void advance(Duration duration) { now = now.plus(duration); }

		@Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(java.time.ZoneId zone) { return this; }
		@Override public Instant instant() { return now; }
	}
}
