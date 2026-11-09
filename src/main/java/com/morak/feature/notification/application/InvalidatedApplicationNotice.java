package com.morak.feature.notification.application;

import java.time.Instant;

public interface InvalidatedApplicationNotice {

	void send(long recipientId, long challengeId, long participationId, Instant now);
}
