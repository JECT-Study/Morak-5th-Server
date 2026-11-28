package com.morak.feature.ad.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.ad.application.dto.result.AdRewardResult;
import com.morak.feature.ad.application.dto.result.AdSsvCallback;
import com.morak.feature.ad.application.port.out.AdRewardPoints;
import com.morak.feature.ad.application.port.out.AdSsvVerifier;
import com.morak.feature.ad.application.port.out.AdTokenCodec;
import com.morak.feature.ad.domain.exception.AdErrorCode;
import com.morak.feature.ad.domain.policy.AdRewardPolicy;
import com.morak.feature.ad.domain.vo.AdToken;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 실광고(AdMob) 시청 완료 처리. 구글이 서버로 보내는 SSV 알림의 서명을 확인한 뒤 500P를 지급한다.
 * 클라이언트가 보낸 "봤다"는 말은 믿지 않고, 구글 서명이 붙은 알림만 믿는다.
 *
 * <p>시청 토큰(광고 시청 시작 API가 발급)은 앱이 SSV 옵션의 customData로 구글에 넘기고, 구글이 그대로 돌려준다.
 * 토큰 id가 원장 멱등키(AD:{id})라서 같은 알림이 다시 와도 한 번만 지급되고, 하루 5회는 모의 광고와 같은 경로로 막는다.
 * 별도 광고 세션 테이블은 두지 않는다.
 */
@Service
public class ClaimAdRewardBySsvService {

	private final AdSsvVerifier verifier;
	private final AdTokenCodec codec;
	private final AdRewardPoints points;
	private final boolean enabled;
	private final Set<String> allowedAdUnits;

	// TODO(D-46): 실광고 서버 검증(SSV). PM 확인 전 제안이다(O-11).
	public ClaimAdRewardBySsvService(
			AdSsvVerifier verifier,
			AdTokenCodec codec,
			AdRewardPoints points,
			@Value("${morak.ad.ssv.enabled:false}") boolean enabled,
			@Value("${morak.ad.ssv.ad-units:}") List<String> adUnits) {
		this.verifier = verifier;
		this.codec = codec;
		this.points = points;
		this.enabled = enabled;
		this.allowedAdUnits = adUnits.stream()
				.map(ClaimAdRewardBySsvService::normalizeAdUnit)
				.filter(unit -> !unit.isEmpty())
				.collect(Collectors.toUnmodifiableSet());
	}

	public AdRewardResult claim(String rawQuery) {
		if (!enabled || allowedAdUnits.isEmpty()) {
			throw new BusinessException(AdErrorCode.UNAVAILABLE,
					"morak.ad.ssv 미설정 enabled=" + enabled + " adUnits=" + allowedAdUnits.size());
		}
		AdSsvCallback callback = verifier.verify(rawQuery);
		// 다른 AdMob 계정의 광고도 구글 서명은 정상이다. 우리 광고 단위가 아니면 지급하지 않는다.
		if (!allowedAdUnits.contains(normalizeAdUnit(callback.adUnit()))) {
			throw new BusinessException(AdErrorCode.INVALID_SSV,
					"허용하지 않은 광고 단위 adUnit=" + callback.adUnit() + " transactionId=" + callback.transactionId());
		}
		AdToken token = codec.parse(callback.customData());
		if (!String.valueOf(token.userId()).equals(callback.userId())) {
			throw new BusinessException(AdErrorCode.INVALID_TOKEN,
					"토큰 주인과 SSV 사용자가 다름 tokenUserId=" + token.userId() + " ssvUserId=" + callback.userId()
							+ " tokenId=" + token.id() + " transactionId=" + callback.transactionId());
		}
		// 서버 시계가 아니라 구글이 서명한 보상 시각으로 본다. 알림이 늦게 오거나 다시 와도 결과가 같다.
		if (!callback.rewardedAt().isBefore(token.expiresAt())) {
			throw new BusinessException(AdErrorCode.EXPIRED_TOKEN,
					"userId=" + token.userId() + " tokenId=" + token.id() + " rewardedAt=" + callback.rewardedAt()
							+ " expiresAt=" + token.expiresAt() + " transactionId=" + callback.transactionId());
		}
		long balance = points.grantReward(
				token.userId(), AdRewardPolicy.idempotencyKey(token.id()), AdRewardPolicy.REWARD_POINT,
				AdRewardPolicy.DAILY_LIMIT);
		int remaining = Math.max(0, AdRewardPolicy.DAILY_LIMIT - points.todayRewardCount(token.userId()));
		return new AdRewardResult(AdRewardPolicy.REWARD_POINT, balance, remaining);
	}

	/**
	 * 광고 단위 ID는 "ca-app-pub-123/456"과 "456" 두 모양으로 쓰일 수 있어 마지막 '/' 뒤만 비교한다.
	 */
	private static String normalizeAdUnit(String adUnit) {
		String trimmed = adUnit == null ? "" : adUnit.trim();
		return trimmed.substring(trimmed.lastIndexOf('/') + 1);
	}
}
