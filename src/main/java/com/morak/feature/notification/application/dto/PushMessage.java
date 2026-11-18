package com.morak.feature.notification.application.dto;

/**
 * 푸시 한 건.
 *
 * @param token 받을 기기의 푸시 토큰
 * @param url 알림을 눌렀을 때 앱이 이동할 경로. 도메인 없이 경로만 (예: /challenges/123). 이동할 곳이 없으면 null
 */
public record PushMessage(String token, String title, String body, String url) {}
