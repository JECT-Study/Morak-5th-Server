package com.morak.feature.notification.application.port.out;

import java.util.Map;

/** 푸시를 보낸 뒤 일정 시간이 지나 영수증을 확인하도록 예약한다. */
public interface PushReceiptScheduler {

	/** ticketToToken: 접수 ID와, 그 푸시를 보낸 토큰. */
	void schedule(Map<String, String> ticketToToken);
}
