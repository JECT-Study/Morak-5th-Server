package com.morak.feature.challenge.application.port.out;

import java.time.Instant;

/** 무효화된 신청을 받은 방장에게 개설 트랜잭션 안에서 인앱 알림을 기록한다. */
public interface InvalidatedApplicationNotice {

	void send(long recipientId, long challengeId, long participationId, Instant now);
}
