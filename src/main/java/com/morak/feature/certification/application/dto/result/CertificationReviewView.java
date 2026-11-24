package com.morak.feature.certification.application.dto.result;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 운영진 인증 검수 화면(위키 §4.10)의 조회 결과. */
public final class CertificationReviewView {

	private CertificationReviewView() {}

	/**
	 * 검수 목록.
	 *
	 * @param totalCount 현재 조건에 맞는 전체 건수(목록은 최대 {@link #LIMIT}건만 담는다)
	 * @param pendingCount 조건과 무관한 전체 미검수 건수
	 */
	public record Page(List<Item> items, int totalCount, int pendingCount) {
		public static final int LIMIT = 100;
	}

	/** @param status PENDING(검수 중) / APPROVED / AUTO_APPROVED / REJECTED */
	public record Item(
			long certificationId,
			long challengeId,
			String challengeTitle,
			String submitterNickname,
			LocalDate certDate,
			Instant submittedAt,
			String status) {}

	/** 목록 필터의 챌린지 선택지. 운영진 검수 대상(포인트 공개) 챌린지만 담는다. */
	public record ChallengeOption(long challengeId, String title) {}

	/**
	 * 인증 상세.
	 *
	 * @param submitterRole HOST(방장) / DEPUTY(부방장) / MEMBER(멤버)
	 * @param commonMission 챌린지 공통 미션
	 * @param verificationRules 개설 시 정한 인증 방법·규칙
	 * @param imageUrls 원본 사진 주소. 사진 저장소 주소가 설정되지 않았으면 비어 있다
	 * @param judge 처리된 인증만 채워진다(처리 운영진·시각·거절 사유)
	 */
	public record Detail(
			long certificationId,
			long challengeId,
			String challengeTitle,
			String submitterNickname,
			String submitterRole,
			LocalDate certDate,
			Instant submittedAt,
			String status,
			String commonMission,
			String verificationRules,
			List<String> imageUrls,
			Judge judge) {}

	public record Judge(String judgeNickname, Instant judgedAt, String rejectReason) {}
}
