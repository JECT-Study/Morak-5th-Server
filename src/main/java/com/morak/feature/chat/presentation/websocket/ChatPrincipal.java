package com.morak.feature.chat.presentation.websocket;

import java.security.Principal;

/** STOMP 세션의 사용자. 이름은 userId이며, /user/queue 로 특정 사용자에게 보낼 때의 주소로도 쓰인다. */
public record ChatPrincipal(Long userId) implements Principal {

	@Override
	public String getName() {
		return String.valueOf(userId);
	}
}
