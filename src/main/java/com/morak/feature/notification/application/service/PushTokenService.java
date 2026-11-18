package com.morak.feature.notification.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.notification.domain.entity.PushChannel;
import com.morak.feature.notification.domain.entity.PushToken;
import com.morak.feature.notification.domain.exception.NotificationErrorCode;
import com.morak.feature.notification.domain.repository.PushTokenRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 앱이 보내 준 푸시 토큰을 저장·삭제한다. 푸시 발송은 하지 않는다. */
@Service
public class PushTokenService {

	/** Expo 토큰 형식. 예: ExponentPushToken[xxxx] */
	private static final Pattern EXPO_TOKEN = Pattern.compile("^Expo(nent)?PushToken\\[[^\\]\\s]+\\]$");

	private final PushTokenRepository pushTokenRepository;
	private final Clock clock;

	public PushTokenService(PushTokenRepository pushTokenRepository, Clock clock) {
		this.pushTokenRepository = pushTokenRepository;
		this.clock = clock;
	}

	/**
	 * 토큰을 저장한다. 같은 토큰이 이미 있으면 지금 사용자의 것으로 덮어쓰므로, 같은 요청을 여러 번 보내도 결과가 같다.
	 * 로그아웃 후 같은 기기에서 다른 사람이 로그인해도 토큰이 한 건만 남는다.
	 * userAgent는 참고용이라 너무 길면 잘라서 저장한다.
	 */
	@Transactional
	public void register(Long userId, String token, String userAgent) {
		validateExpoToken(userId, token);
		// PostgreSQL은 마이크로초까지만 저장하므로 미리 잘라 둔다.
		LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
		pushTokenRepository.register(userId, PushChannel.EXPO, token, normalizeUserAgent(userAgent), now);
	}

	/** 본인 토큰을 삭제한다. 이미 없거나 남의 토큰이면 아무것도 지우지 않고 성공한다 (로그아웃이 막히지 않게). */
	@Transactional
	public void unregister(Long userId, String token) {
		if (token == null || token.isBlank() || token.length() > PushToken.MAX_TOKEN_LENGTH) {
			throw new BusinessException(NotificationErrorCode.INVALID_PUSH_TOKEN, "userId=" + userId + " blank or too long");
		}
		pushTokenRepository.delete(userId, token);
	}

	/** 푸시 서비스가 더 이상 쓸 수 없다고 알려 준 토큰을 주인과 상관없이 지운다. 지운 개수를 돌려준다. */
	@Transactional
	public int removeUnusable(Collection<String> tokens) {
		return tokens.isEmpty() ? 0 : pushTokenRepository.deleteByTokens(tokens);
	}

	private static void validateExpoToken(Long userId, String token) {
		if (token == null || token.length() > PushToken.MAX_TOKEN_LENGTH || !EXPO_TOKEN.matcher(token).matches()) {
			// 토큰은 비밀 값에 가까우므로 길이만 남긴다.
			throw new BusinessException(
					NotificationErrorCode.INVALID_PUSH_TOKEN,
					"userId=" + userId + " tokenLength=" + (token == null ? null : token.length()));
		}
	}

	private static String normalizeUserAgent(String userAgent) {
		if (userAgent == null || userAgent.isBlank()) {
			return null;
		}
		String trimmed = userAgent.trim();
		return trimmed.length() > PushToken.MAX_USER_AGENT_LENGTH
				? trimmed.substring(0, PushToken.MAX_USER_AGENT_LENGTH)
				: trimmed;
	}
}
