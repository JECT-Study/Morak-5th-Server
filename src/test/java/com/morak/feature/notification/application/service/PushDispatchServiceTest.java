package com.morak.feature.notification.application.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.feature.notification.application.dto.NotificationSavedEvent;
import com.morak.feature.notification.application.dto.PushMessage;
import com.morak.feature.notification.application.dto.PushReceipt;
import com.morak.feature.notification.application.dto.PushTicket;
import com.morak.feature.notification.application.port.out.PushGateway;
import com.morak.feature.notification.application.port.out.PushReceiptScheduler;
import com.morak.feature.notification.domain.entity.NotificationType;
import com.morak.feature.notification.domain.entity.PushChannel;
import com.morak.feature.notification.support.InMemoryPushTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class PushDispatchServiceTest {

	private static final Long USER = 10L;
	private static final Long OTHER = 20L;

	private final InMemoryPushTokenRepository tokens = new InMemoryPushTokenRepository();
	private final FakeGateway gateway = new FakeGateway();
	private final List<Map<String, String>> scheduledReceipts = new ArrayList<>();
	private final PushReceiptScheduler receiptScheduler = scheduledReceipts::add;
	private final PushDispatchService service = new PushDispatchService(
			tokens,
			new PushTokenService(tokens, Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneOffset.UTC)),
			gateway,
			receiptScheduler);

	private static NotificationSavedEvent event(Long recipient, String targetType, Long targetId) {
		return new NotificationSavedEvent(
				NotificationType.PARTICIPATION_APPROVED,
				recipient,
				"참여 확정",
				"매일 아침 러닝 참여가 확정됐어요",
				targetType,
				targetId);
	}

	private void register(Long user, String token) {
		tokens.register(user, PushChannel.EXPO, token, null, null);
	}

	@Test
	void sendsTitleBodyAndUrlToEveryEnabledTokenOfRecipientOnly() {
		register(USER, "ExponentPushToken[a]");
		register(USER, "ExponentPushToken[b]");
		register(OTHER, "ExponentPushToken[other]");

		service.dispatch(event(USER, "CHALLENGE", 5L));

		assertEquals(1, gateway.sent.size());
		assertEquals(
				List.of(
						new PushMessage("ExponentPushToken[a]", "참여 확정", "매일 아침 러닝 참여가 확정됐어요", "/challenges/5"),
						new PushMessage("ExponentPushToken[b]", "참여 확정", "매일 아침 러닝 참여가 확정됐어요", "/challenges/5")),
				gateway.sent.get(0));
	}

	@Test
	void sendsNothingWhenRecipientHasNoTokens() {
		service.dispatch(event(USER, "CHALLENGE", 5L));

		assertTrue(gateway.sent.isEmpty());
		assertTrue(scheduledReceipts.isEmpty());
	}

	@Test
	void sendsNothingWhenRecipientTurnedPushOff() {
		register(USER, "ExponentPushToken[a]");
		tokens.disabledUsers.add(USER);

		service.dispatch(event(USER, "CHALLENGE", 5L));

		assertTrue(gateway.sent.isEmpty());
	}

	@Test
	void urlIsNullWhenThereIsNoTargetOrNoPathRuleYet() {
		register(USER, "ExponentPushToken[a]");

		service.dispatch(event(USER, null, null));
		service.dispatch(event(USER, "PARTICIPATION", 1L));

		assertNull(gateway.sent.get(0).get(0).url());
		assertNull(gateway.sent.get(1).get(0).url());
	}

	@Test
	void splitsMoreThanOneHundredMessagesIntoBatches() {
		for (int i = 0; i < 250; i++) {
			register(USER, "ExponentPushToken[t" + i + "]");
		}

		service.dispatch(event(USER, "CHALLENGE", 5L));

		assertEquals(List.of(100, 100, 50), gateway.sent.stream().map(List::size).toList());
	}

	@Test
	void acceptedTicketsAreScheduledForReceiptCheck() {
		register(USER, "ExponentPushToken[a]");
		register(USER, "ExponentPushToken[b]");
		gateway.responder = batch -> List.of(PushTicket.accepted("id-a"), PushTicket.accepted("id-b"));

		service.dispatch(event(USER, "CHALLENGE", 5L));

		assertEquals(
				List.of(Map.of("id-a", "ExponentPushToken[a]", "id-b", "ExponentPushToken[b]")), scheduledReceipts);
		assertEquals(2, tokens.tokens.size());
	}

	@Test
	void deviceNotRegisteredTicketDeletesOnlyThatToken() {
		register(USER, "ExponentPushToken[good]");
		register(USER, "ExponentPushToken[gone]");
		register(OTHER, "ExponentPushToken[others]");
		gateway.responder = batch -> List.of(
				PushTicket.accepted("id-good"), PushTicket.rejected(PushGateway.DEVICE_NOT_REGISTERED));

		service.dispatch(event(USER, "CHALLENGE", 5L));

		assertEquals(List.of("ExponentPushToken[good]", "ExponentPushToken[others]"), List.copyOf(tokens.tokens.keySet()));
		assertEquals(List.of(Map.of("id-good", "ExponentPushToken[good]")), scheduledReceipts);
	}

	@Test
	void otherTicketErrorsKeepTheTokenAndSkipReceiptCheck() {
		register(USER, "ExponentPushToken[a]");
		gateway.responder = batch -> List.of(PushTicket.rejected("MessageRateExceeded"));

		service.dispatch(event(USER, "CHALLENGE", 5L));

		assertEquals(1, tokens.tokens.size());
		assertTrue(scheduledReceipts.isEmpty());
	}

	@Test
	void gatewayFailureDoesNotThrowAndKeepsTokens() {
		register(USER, "ExponentPushToken[a]");
		gateway.failure = new IllegalStateException("Expo down");

		assertDoesNotThrow(() -> service.dispatch(event(USER, "CHALLENGE", 5L)));

		assertEquals(1, tokens.tokens.size());
		assertTrue(scheduledReceipts.isEmpty());
	}

	@Test
	void failedBatchDoesNotStopTheNextBatch() {
		for (int i = 0; i < 150; i++) {
			register(USER, "ExponentPushToken[t" + i + "]");
		}
		gateway.failFirstCall = true;
		gateway.responder = batch -> batch.stream().map(m -> PushTicket.accepted("id-" + m.token())).toList();

		service.dispatch(event(USER, "CHALLENGE", 5L));

		assertEquals(2, gateway.sent.size());
		assertEquals(1, scheduledReceipts.size());
		assertEquals(50, scheduledReceipts.get(0).size());
	}

	@Test
	void mismatchedTicketCountDoesNotThrow() {
		register(USER, "ExponentPushToken[a]");
		register(USER, "ExponentPushToken[b]");
		gateway.responder = batch -> List.of(PushTicket.accepted("only-one"));

		assertDoesNotThrow(() -> service.dispatch(event(USER, "CHALLENGE", 5L)));

		assertEquals(List.of(Map.of("only-one", "ExponentPushToken[a]")), scheduledReceipts);
	}

	private static class FakeGateway implements PushGateway {

		final List<List<PushMessage>> sent = new ArrayList<>();
		Function<List<PushMessage>, List<PushTicket>> responder =
				batch -> batch.stream().map(m -> PushTicket.rejected("None")).toList();
		RuntimeException failure;
		boolean failFirstCall;

		@Override
		public List<PushTicket> send(List<PushMessage> messages) {
			sent.add(List.copyOf(messages));
			if (failure != null) {
				throw failure;
			}
			if (failFirstCall && sent.size() == 1) {
				throw new IllegalStateException("first batch fails");
			}
			return responder.apply(messages);
		}

		@Override
		public Map<String, PushReceipt> getReceipts(List<String> ticketIds) {
			throw new UnsupportedOperationException();
		}
	}
}
