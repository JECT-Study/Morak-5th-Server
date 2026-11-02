package com.morak.feature.challenge.application.query;

import java.time.LocalDate;
import java.util.Optional;

/** 다른 모듈(채팅 등)에 공개하는 챌린지 조회 API. */
public interface ChallengeQuery {

	/** 챌린지 종료일 (KST 기준 날짜). 챌린지가 없으면 empty. */
	Optional<LocalDate> findEndDate(Long challengeId);
}
