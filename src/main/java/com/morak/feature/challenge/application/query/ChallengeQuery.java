package com.morak.feature.challenge.application.query;

import java.time.LocalDate;
import java.util.Optional;

/** 다른 모듈(채팅 등)에 공개하는 챌린지 조회 API. */
public interface ChallengeQuery {

	/** 챌린지 종료일 (KST 기준 날짜). 챌린지가 없으면 empty. */
	Optional<LocalDate> findEndDate(Long challengeId);

	/** 인증 제출에 필요한 챌린지 상태·기간·참여 포인트. 챌린지가 없으면 empty. */
	Optional<CertificationWindow> findCertificationWindow(Long challengeId);

	/** 챌린지명. 알림 문구에 쓴다. 챌린지가 없으면 empty. */
	Optional<String> findTitle(Long challengeId);

	/**
	 * @param status challenge.status
	 * @param startDate 시작일 (KST)
	 * @param endDate 종료일 (KST). 조기 종료되면 종료일로 바뀐다
	 * @param pointAmount 참여 포인트. 0보다 크면 운영진이 인증을 판정한다(위키 §4.4)
	 */
	record CertificationWindow(String status, LocalDate startDate, LocalDate endDate, int pointAmount) {}
}
