package com.morak.feature.participation.application.dto.query;

/**
 * 방장이 보는 신청 목록의 구분.
 *
 * <p>TODO(D-25): 쿼리 값(PENDING 기본, ACTIVE, REJECTED)은 PM·프론트 확정 전 제안값이다.
 */
public enum ApplicationListStatus {

	/** 승인 대기(PENDING)와, 방장이 아직 확인하지 않은 무효(INVALIDATED) 신청(위키 §4.3, D-24). */
	PENDING,

	/** 승인되어 참여 중인 신청. 방장 본인은 제외한다. */
	ACTIVE,

	/** 거절한 신청(정원 마감 자동 거절 포함). */
	REJECTED
}
