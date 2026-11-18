package com.morak.feature.notification.application.port.out;

import com.morak.feature.notification.application.dto.PushMessage;
import com.morak.feature.notification.application.dto.PushReceipt;
import com.morak.feature.notification.application.dto.PushTicket;
import java.util.List;
import java.util.Map;

/** 푸시를 실제로 보내는 외부 서비스(Expo Push)와의 경계. 호출이 실패하면 RuntimeException을 던진다. */
public interface PushGateway {

	/** 더 이상 푸시를 받을 수 없는 기기. 이 오류가 오면 그 토큰은 지워야 한다. */
	String DEVICE_NOT_REGISTERED = "DeviceNotRegistered";

	/** 한 번에 보낼 수 있는 최대 건수 (Expo 제한). */
	int MAX_MESSAGES_PER_SEND = 100;

	/** 영수증을 한 번에 조회할 수 있는 최대 건수 (Expo 제한). */
	int MAX_IDS_PER_RECEIPT_REQUEST = 1000;

	/** 메시지를 보내고, 메시지와 같은 순서로 접수 결과를 돌려준다. messages는 MAX_MESSAGES_PER_SEND건 이하여야 한다. */
	List<PushTicket> send(List<PushMessage> messages);

	/** 접수 ID(ticket id)별 영수증. 아직 준비되지 않은 영수증은 결과에 없다. */
	Map<String, PushReceipt> getReceipts(List<String> ticketIds);
}
