package com.morak.feature.chat.presentation.dto.response;

/** 전송 실패 알림. /user/queue/errors 로 보낸 사람에게만 전달된다. 요청에서 읽을 수 없었으면 clientMessageId는 null. */
public record ChatSendErrorResponse(String clientMessageId, String code, String message) {}
