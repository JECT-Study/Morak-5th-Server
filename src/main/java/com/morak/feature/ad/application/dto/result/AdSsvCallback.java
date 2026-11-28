package com.morak.feature.ad.application.dto.result;

import java.time.Instant;

/**
 * 구글이 서명해서 보낸 광고 시청 완료 알림(SSV) 중 우리가 쓰는 값.
 * reward_amount·reward_item은 받지 않는다. 지급 포인트는 서버 정책(AdRewardPolicy)만 따른다.
 *
 * @param adUnit 광고를 띄운 광고 단위 ID
 * @param userId 앱이 SSV 옵션에 넣은 사용자 ID
 * @param customData 앱이 SSV 옵션에 넣은 시청 토큰(광고 시청 시작 API가 준 값)
 * @param transactionId 구글이 붙인 이 보상의 고유 ID (로그용)
 * @param rewardedAt 구글이 보상을 확정한 시각
 */
public record AdSsvCallback(String adUnit, String userId, String customData, String transactionId, Instant rewardedAt) {}
