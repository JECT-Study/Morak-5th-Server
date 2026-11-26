package com.morak.feature.certification.application.port.out;

import java.time.LocalDate;
import java.util.Optional;

/** 인증 제출이 챌린지·참가 도메인에서 읽는 것들. */
public interface CertificationSubmitTargets {

	/** 챌린지가 없으면 empty. */
	Optional<Challenge> findChallenge(long challengeId);

	/** 그 챌린지에서 사용자의 참가. 참여한 적이 없으면 empty. */
	Optional<Participant> findParticipant(long challengeId, long userId);

	/** @param pointAmount 0보다 크면 운영진이 판정한다(위키 §4.4) */
	record Challenge(boolean inProgress, LocalDate startDate, LocalDate endDate, int pointAmount) {}

	record Participant(long participationId, boolean active) {}
}
