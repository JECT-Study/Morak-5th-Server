package com.morak.feature.certification.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 인증 피드 조회. */
public interface CertificationFeedStore {

	/**
	 * 그 챌린지·날짜의 피드 행. 승인된 인증(APPROVED·AUTO_APPROVED)과, 조회자 본인의 인증(FAILED 제외)을 제출 순으로 돌려준다.
	 *
	 * @param viewerParticipationId 조회자의 참가 ID
	 */
	List<FeedRow> find(long challengeId, LocalDate certDate, long viewerParticipationId);

	/** @param imageKey 사진이 없으면 null */
	record FeedRow(long certificationId, long participationId, String status, Instant submittedAt, String imageKey) {}
}
