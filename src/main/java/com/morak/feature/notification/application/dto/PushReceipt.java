package com.morak.feature.notification.application.dto;

/**
 * 푸시가 기기까지 전달됐는지에 대한 영수증.
 *
 * @param error 실패했으면 오류 이름 (예: DeviceNotRegistered). 성공했으면 null
 */
public record PushReceipt(String error) {

	public static PushReceipt ok() {
		return new PushReceipt(null);
	}

	public static PushReceipt failed(String error) {
		return new PushReceipt(error);
	}

	public boolean isOk() {
		return error == null;
	}
}
