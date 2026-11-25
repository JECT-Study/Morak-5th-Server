package com.morak.feature.certification.application.dto.result;

/**
 * 좋아요를 누르거나 취소한 뒤의 상태.
 *
 * @param liked 조회자가 지금 눌러 둔 상태인지
 */
public record CertificationLike(long certificationId, boolean liked, int likeCount) {}
