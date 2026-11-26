package com.morak.feature.certification.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** 퇴장·조기 종료를 참가 도메인에 맡긴다. 호출한 트랜잭션에 함께 묶인다. */
public interface CertificationExpelTargets {

	/** 참여 중인 대상을 퇴장시키고, 공개 챌린지가 2명 이하로 남으면 조기 종료한다. 이미 끝난 대상은 건너뛴다. */
	Outcome expel(long challengeId, Collection<Long> participationIds, LocalDate today, Instant now);

	/** @param earlyEndedUserIds 조기 종료됐으면 남은 참여자, 아니면 비어 있다 */
	record Outcome(List<Expelled> expelled, List<Long> earlyEndedUserIds) {}

	record Expelled(long participationId, long userId) {}
}
