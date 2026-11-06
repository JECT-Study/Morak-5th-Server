package com.morak.feature.chat.application.port.out;

import java.time.Instant;
import java.util.Optional;

/** 웹소켓 연결용 1회용 티켓 저장소. */
public interface ChatTicketStore {

	/** 티켓을 저장한다. expiresAt이 지나면 꺼낼 수 없다. */
	void save(String ticket, Long userId, Instant expiresAt);

	/** 티켓을 꺼내면서 삭제한다. 같은 티켓은 한 번만 꺼낼 수 있고, 없거나 만료됐으면 empty. */
	Optional<Long> consume(String ticket);
}
