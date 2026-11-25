package com.morak.feature.certification.application.dto.result;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 한 날짜의 인증 피드. 제출 순이다. */
public record CertificationFeed(LocalDate certDate, List<Item> items) {

	/**
	 * @param status 다른 팀원의 인증은 APPROVED·AUTO_APPROVED만 온다. 본인 인증은 PENDING·REJECTED도 온다
	 * @param imageUrl 열람용 서명 주소(300초). 서명에 실패하면 null
	 * @param mine 조회자 본인의 인증인지
	 * @param likeCount 좋아요 수
	 * @param liked 조회자가 좋아요를 눌렀는지
	 */
	public record Item(long certificationId, long userId, String nickname, String profileImageUrl, String role,
			String status, Instant submittedAt, String imageUrl, boolean mine, int likeCount, boolean liked) {}
}
