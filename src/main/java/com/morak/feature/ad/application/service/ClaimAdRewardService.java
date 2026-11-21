package com.morak.feature.ad.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.ad.application.dto.result.AdRewardResult;
import com.morak.feature.ad.application.port.out.AdRewardPoints;
import com.morak.feature.ad.application.port.out.AdTokenCodec;
import com.morak.feature.ad.domain.exception.AdErrorCode;
import com.morak.feature.ad.domain.policy.AdRewardPolicy;
import com.morak.feature.ad.domain.vo.AdToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모의 광고 시청 완료 처리. 토큰(서명·주인·만료·최소 시청 시간)을 확인하고 500P를 지급한다.
 * 한도 확인과 지급은 포인트 도메인이 계정 행을 잠근 채 한 트랜잭션으로 처리한다.
 * 실광고(SSV)가 붙으면 같은 지급 메서드를 호출하되, 이 API는 mock-enabled=false로 닫는다.
 */
@Service
public class ClaimAdRewardService {

	private final AdRewardPoints points;
	private final AdTokenCodec codec;
	private final Clock clock;
	private final boolean mockEnabled;
	private final Duration minWatch;

	// TODO(D-8): 모의 광고. 실광고 연동 후 운영에서는 mock-enabled를 끈다(O-11).
	public ClaimAdRewardService(
			AdRewardPoints points,
			AdTokenCodec codec,
			Clock clock,
			@Value("${morak.ad.mock-enabled:false}") boolean mockEnabled,
			@Value("${morak.ad.min-watch:PT5S}") Duration minWatch) {
		this.points = points;
		this.codec = codec;
		this.clock = clock;
		this.mockEnabled = mockEnabled;
		this.minWatch = minWatch;
	}

	@Transactional
	public AdRewardResult claim(long userId, String rawToken) {
		if (!mockEnabled) {
			throw new BusinessException(AdErrorCode.UNAVAILABLE, "morak.ad.mock-enabled=false userId=" + userId);
		}
		AdToken token = codec.parse(rawToken);
		if (token.userId() != userId) {
			throw new BusinessException(AdErrorCode.INVALID_TOKEN,
					"다른 사용자의 토큰 userId=" + userId + " tokenUserId=" + token.userId() + " tokenId=" + token.id());
		}
		Instant now = clock.instant();
		if (token.isExpired(now)) {
			throw new BusinessException(AdErrorCode.EXPIRED_TOKEN,
					"userId=" + userId + " tokenId=" + token.id() + " expiresAt=" + token.expiresAt());
		}
		if (!token.watchedAtLeast(now, minWatch)) {
			throw new BusinessException(AdErrorCode.WATCH_TIME_NOT_ENOUGH,
					"userId=" + userId + " tokenId=" + token.id() + " issuedAt=" + token.issuedAt() + " minWatch=" + minWatch);
		}
		long balance = points.grantReward(
				userId, AdRewardPolicy.idempotencyKey(token.id()), AdRewardPolicy.REWARD_POINT, AdRewardPolicy.DAILY_LIMIT);
		int remaining = Math.max(0, AdRewardPolicy.DAILY_LIMIT - points.todayRewardCount(userId));
		return new AdRewardResult(AdRewardPolicy.REWARD_POINT, balance, remaining);
	}
}
