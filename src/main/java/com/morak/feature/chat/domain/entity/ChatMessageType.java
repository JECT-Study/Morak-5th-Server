package com.morak.feature.chat.domain.entity;

public enum ChatMessageType {
	/** 참가자가 보낸 메시지 */
	USER,
	/** 서버가 올리는 안내 메시지 (판정 경고 누적 안내 등). 보낸 참가자가 없다. */
	SYSTEM
}
