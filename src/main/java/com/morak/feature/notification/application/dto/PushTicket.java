package com.morak.feature.notification.application.dto;

/**
 * 푸시를 보낸 직후 받는 접수 결과. 실제 기기 전달 여부는 나중에 영수증(PushReceipt)으로 확인한다.
 *
 * @param id 접수됐으면 영수증 조회에 쓰는 ID
 * @param error 접수되지 않았으면 오류 이름 (예: DeviceNotRegistered). 접수됐으면 null
 */
public record PushTicket(String id, String error) {

	public static PushTicket accepted(String id) {
		return new PushTicket(id, null);
	}

	public static PushTicket rejected(String error) {
		return new PushTicket(null, error);
	}

	public boolean isAccepted() {
		return error == null && id != null;
	}
}
