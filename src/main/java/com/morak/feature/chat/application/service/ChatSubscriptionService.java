package com.morak.feature.chat.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.chat.application.port.out.ChatParticipantReader;
import com.morak.feature.chat.domain.exception.ChatErrorCode;
import org.springframework.stereotype.Service;

@Service
public class ChatSubscriptionService {

	private final ChatParticipantReader participantReader;

	public ChatSubscriptionService(ChatParticipantReader participantReader) {
		this.participantReader = participantReader;
	}

	/**
	 * 채팅방 실시간 구독 권한 검사.
	 * 채팅방 이용 대상(ACTIVE, COMPLETED)이면 구독할 수 있다. 종료 후에도 읽을 수 있어야 하므로 COMPLETED도 허용한다.
	 */
	public void checkSubscribable(Long challengeId, Long userId) {
		participantReader.findChatParticipant(challengeId, userId)
				.orElseThrow(() -> new BusinessException(ChatErrorCode.NOT_PARTICIPANT));
	}
}
