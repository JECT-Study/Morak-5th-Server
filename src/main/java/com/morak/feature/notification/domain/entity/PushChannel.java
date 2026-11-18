package com.morak.feature.notification.domain.entity;

/**
 * 푸시를 보내는 경로.
 *
 * TODO(D-21): 위키 O-10(푸시 채널)이 아직 미정이다. 프론트가 Expo Push로 구현하기로 해 EXPO만 둔다.
 * PM 확인으로 채널이 확정되면 위키를 갱신하고, WEB·FCM이 필요하면 그때 추가한다.
 */
public enum PushChannel {
	EXPO
}
