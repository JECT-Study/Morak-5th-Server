package com.morak.feature.challenge.application.dto.query;

/**
 * 내 챌린지 목록의 구분.
 *
 * <p>TODO(D-23): 상태 분류는 PM·프론트 확정 전 제안값이다.
 */
public enum MyChallengeFilter {

	/** 참여 중(ACTIVE)이고 챌린지가 모집·시작 보류·진행·정산 대기(RECRUITING, START_HELD, IN_PROGRESS, FINALIZING)인 것. 페이지 없음. */
	HOME,

	/** 퇴장·완주·조기 종료·폐기(EXPELLED, COMPLETED, EARLY_ENDED, DISCARDED)된 참가. 커서 페이지. */
	ARCHIVE
}
