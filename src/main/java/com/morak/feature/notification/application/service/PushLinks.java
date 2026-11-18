package com.morak.feature.notification.application.service;

/**
 * 알림의 이동 대상(target_type, target_id)을 앱이 열 경로로 바꾼다. 앱은 푸시의 data.url을 받아 이동하므로
 * 도메인 없이 경로만 만든다 (예: /challenges/123).
 *
 * TODO(D-22): 이동 경로 규칙은 위키에 없는 제안이다. 대상 종류가 늘면 PM·프론트와 맞춰 추가한다.
 */
final class PushLinks {

	private PushLinks() {}

	/** 이동할 곳이 없거나 아직 경로 규칙이 없는 대상이면 null. */
	static String pathOf(String targetType, Long targetId) {
		if (targetType == null || targetId == null) {
			return null;
		}
		return switch (targetType) {
			case "CHALLENGE" -> "/challenges/" + targetId;
			default -> null;
		};
	}
}
