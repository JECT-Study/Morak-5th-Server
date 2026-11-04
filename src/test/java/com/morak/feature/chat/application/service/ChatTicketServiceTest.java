package com.morak.feature.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.morak.common.exception.BusinessException;
import com.morak.feature.chat.application.dto.ChatTicketResult;
import com.morak.feature.chat.domain.exception.ChatErrorCode;
import com.morak.feature.chat.infrastructure.ticket.InMemoryChatTicketStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ChatTicketServiceTest {

	private final MutableClock clock = new MutableClock(Instant.parse("2026-11-05T00:00:00Z"));
	private final ChatTicketService service = new ChatTicketService(new InMemoryChatTicketStore(clock), clock);

	@Test
	void issuedTicketResolvesToUserId() {
		ChatTicketResult result = service.issue(7L);

		assertEquals(30, result.expiresIn());
		assertEquals(7L, service.consume(result.ticket()));
	}

	@Test
	void ticketCanBeUsedOnlyOnce() {
		String ticket = service.issue(7L).ticket();
		service.consume(ticket);

		assertInvalid(ticket);
	}

	@Test
	void ticketExpiresAfterThirtySeconds() {
		String usedInTime = service.issue(7L).ticket();
		String expired = service.issue(7L).ticket();

		clock.advance(Duration.ofSeconds(30).minusMillis(1));
		assertEquals(7L, service.consume(usedInTime));

		clock.advance(Duration.ofMillis(1));
		assertInvalid(expired);
	}

	@Test
	void unknownOrBlankTicketIsRejected() {
		assertInvalid("unknown");
		assertInvalid("");
		assertInvalid(null);
	}

	@Test
	void ticketsAreUnique() {
		assertNotEquals(service.issue(7L).ticket(), service.issue(7L).ticket());
	}

	private void assertInvalid(String ticket) {
		BusinessException exception = assertThrows(BusinessException.class, () -> service.consume(ticket));
		assertEquals(ChatErrorCode.INVALID_TICKET, exception.getErrorCode());
	}

	private static class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override public ZoneId getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(ZoneId zone) { return this; }
		@Override public Instant instant() { return now; }
	}
}
