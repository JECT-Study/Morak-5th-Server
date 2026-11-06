package com.morak.feature.chat.presentation.dto.request;

/**
 * STOMP SEND(/app/challenges/{challengeId}/chat) 본문.
 * clientMessageId는 문자열로 받아 컨트롤러에서 UUID로 검증한다. 형식 오류도 전송 실패 알림으로 돌려주기 위해서다.
 */
public record ChatSendRequest(String clientMessageId, String content, Long replyCertificationId) {}
