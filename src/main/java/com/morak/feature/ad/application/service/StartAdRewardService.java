package com.morak.feature.ad.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.ad.application.dto.result.AdRewardSession;
import com.morak.feature.ad.application.port.out.AdRewardPoints;
import com.morak.feature.ad.application.port.out.AdTokenCodec;
import com.morak.feature.ad.domain.exception.AdErrorCode;
import com.morak.feature.ad.domain.policy.AdRewardPolicy;
import com.morak.feature.ad.domain.vo.AdToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 광고 시청을 시작할 때 1회용 시청 토큰을 발급한다. 시청을 시작만 하는 것은 횟수에 포함되지 않는다.
 * 횟수는 보상을 지급할 때만 늘어나므로, 오늘 한도를 이미 채웠을 때만 여기서 거절한다.
 */
@Service
public class StartAdRewardService {

	private final AdRewardPoints points;
	private final AdTokenCodec codec;
	private final Clock clock;
	private final Duration minWatch;
	private final Duration tokenValidity;

	// TODO(D-8): 최소 시청 시간과 토큰 만료 시간은 PM 확인 전 임시값이다. 실광고 연동 때 광고 길이에 맞춰 다시 정한다.
	public StartAdRewardService(
			AdRewardPoints points,
			AdTokenCodec codec,
			Clock clock,
			@Value("${morak.ad.min-watch:PT5S}") Duration minWatch,
			@Value("${morak.ad.token-validity:PT10M}") Duration tokenValidity) {
		this.points = points;
		this.codec = codec;
		this.clock = clock;
		this.minWatch = minWatch;
		this.tokenValidity = tokenValidity;
	}

	public AdRewardSession start(long userId) {
		int used = points.todayRewardCount(userId);
		if (used >= AdRewardPolicy.DAILY_LIMIT) {
			throw new BusinessException(AdErrorCode.DAILY_LIMIT_EXCEEDED,
					"userId=" + userId + " used=" + used + " dailyLimit=" + AdRewardPolicy.DAILY_LIMIT);
		}
		Instant now = clock.instant();
		AdToken token = new AdToken(UUID.randomUUID().toString(), userId, now, now.plus(tokenValidity));
		return new AdRewardSession(
				codec.issue(token), minWatch.toSeconds(), token.expiresAt(), AdRewardPolicy.DAILY_LIMIT - used);
	}
}
