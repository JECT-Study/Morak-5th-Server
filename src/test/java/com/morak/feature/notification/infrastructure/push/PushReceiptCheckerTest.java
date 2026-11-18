package com.morak.feature.notification.infrastructure.push;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.feature.notification.application.dto.PushMessage;
import com.morak.feature.notification.application.dto.PushReceipt;
import com.morak.feature.notification.application.dto.PushTicket;
import com.morak.feature.notification.application.port.out.PushGateway;
import com.morak.feature.notification.application.service.PushTokenService;
import com.morak.feature.notification.domain.entity.PushChannel;
import com.morak.feature.notification.support.InMemoryPushTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PushReceiptCheckerTest {

	private final InMemoryPushTokenRepository tokens = new InMemoryPushTokenRepository();
	private final FakeGateway gateway = new FakeGateway();
	private final PushReceiptChecker checker = new PushReceiptChecker(
			gateway,
			new PushTokenService(tokens, Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneOffset.UTC)),
			Duration.ofMillis(30));

	@AfterEach
	void stop() {
		checker.shutdown();
	}

	private void register(String token) {
		tokens.register(10L, PushChannel.EXPO, token, null, null);
	}

	@Test
	void deletesOnlyTokensWhoseReceiptSaysDeviceNotRegistered() {
		register("ExponentPushToken[ok]");
		register("ExponentPushToken[gone]");
		register("ExponentPushToken[other-error]");
		register("ExponentPushToken[no-receipt]");
		gateway.receipts.put("t-ok", PushReceipt.ok());
		gateway.receipts.put("t-gone", PushReceipt.failed(PushGateway.DEVICE_NOT_REGISTERED));
		gateway.receipts.put("t-err", PushReceipt.failed("MessageTooBig"));

		checker.check(Map.of(
				"t-ok", "ExponentPushToken[ok]",
				"t-gone", "ExponentPushToken[gone]",
				"t-err", "ExponentPushToken[other-error]",
				"t-none", "ExponentPushToken[no-receipt]"));

		assertEquals(
				List.of("ExponentPushToken[ok]", "ExponentPushToken[other-error]", "ExponentPushToken[no-receipt]"),
				List.copyOf(tokens.tokens.keySet()));
	}

	@Test
	void requestsReceiptsInBatchesOfOneThousand() {
		Map<String, String> tickets = new LinkedHashMap<>();
		for (int i = 0; i < 2_500; i++) {
			tickets.put("t" + i, "ExponentPushToken[x" + i + "]");
		}

		checker.check(tickets);

		assertEquals(List.of(1_000, 1_000, 500), gateway.requestedSizes);
	}

	@Test
	void gatewayFailureDoesNotThrowAndKeepsTokens() {
		register("ExponentPushToken[a]");
		gateway.failure = new IllegalStateException("Expo down");

		assertDoesNotThrow(() -> checker.check(Map.of("t1", "ExponentPushToken[a]")));

		assertEquals(1, tokens.tokens.size());
	}

	@Test
	void scheduledCheckRunsAfterDelay() throws Exception {
		register("ExponentPushToken[gone]");
		gateway.receipts.put("t1", PushReceipt.failed(PushGateway.DEVICE_NOT_REGISTERED));
		gateway.called = new CountDownLatch(1);

		checker.schedule(Map.of("t1", "ExponentPushToken[gone]"));

		assertTrue(gateway.called.await(5, TimeUnit.SECONDS));
		long deadline = System.currentTimeMillis() + 5_000;
		while (!tokens.tokens.isEmpty() && System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}
		assertTrue(tokens.tokens.isEmpty());
	}

	@Test
	void emptyScheduleDoesNothing() {
		checker.schedule(Map.of());

		assertTrue(gateway.requestedSizes.isEmpty());
	}

	private static class FakeGateway implements PushGateway {

		final Map<String, PushReceipt> receipts = new LinkedHashMap<>();
		final List<Integer> requestedSizes = new ArrayList<>();
		RuntimeException failure;
		CountDownLatch called;

		@Override
		public List<PushTicket> send(List<PushMessage> messages) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Map<String, PushReceipt> getReceipts(List<String> ticketIds) {
			requestedSizes.add(ticketIds.size());
			if (called != null) {
				called.countDown();
			}
			if (failure != null) {
				throw failure;
			}
			Map<String, PushReceipt> result = new LinkedHashMap<>();
			for (String id : ticketIds) {
				if (receipts.containsKey(id)) {
					result.put(id, receipts.get(id));
				}
			}
			return result;
		}
	}
}
