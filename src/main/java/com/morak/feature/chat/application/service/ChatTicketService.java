package com.morak.feature.chat.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.chat.application.dto.ChatTicketResult;
import com.morak.feature.chat.application.port.out.ChatTicketStore;
import com.morak.feature.chat.domain.exception.ChatErrorCode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import org.springframework.stereotype.Service;

/**
 * 웹소켓 연결용 1회용 티켓.
 * 브라우저는 JWT(HttpOnly 쿠키)를 직접 읽을 수 없으므로, BFF가 JWT로 티켓을 발급받아 브라우저에 넘기고
 * 브라우저는 STOMP CONNECT 때 이 티켓으로 신원을 증명한다.
 */
@Service
public class ChatTicketService {

	public static final Duration TICKET_TTL = Duration.ofSeconds(30);
	private static final int TICKET_BYTES = 32;

	private final ChatTicketStore ticketStore;
	private final Clock clock;
	private final SecureRandom secureRandom = new SecureRandom();

	public ChatTicketService(ChatTicketStore ticketStore, Clock clock) {
		this.ticketStore = ticketStore;
		this.clock = clock;
	}

	/** 티켓 발급. 30초 동안 한 번만 사용할 수 있다. */
	public ChatTicketResult issue(Long userId) {
		String ticket = newTicket();
		ticketStore.save(ticket, userId, clock.instant().plus(TICKET_TTL));
		return new ChatTicketResult(ticket, TICKET_TTL.toSeconds());
	}

	/** 티켓을 사용하고 발급받은 userId를 반환한다. 없거나 만료됐거나 이미 사용한 티켓이면 INVALID_TICKET. */
	public Long consume(String ticket) {
		if (ticket == null || ticket.isBlank()) {
			throw new BusinessException(ChatErrorCode.INVALID_TICKET);
		}
		return ticketStore.consume(ticket)
				.orElseThrow(() -> new BusinessException(ChatErrorCode.INVALID_TICKET));
	}

	private String newTicket() {
		byte[] bytes = new byte[TICKET_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
