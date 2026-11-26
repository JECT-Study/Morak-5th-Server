package com.morak.feature.certification.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 결과 확정의 상태 변경을 챌린지·참가 도메인에 맡긴다. 모두 호출한 트랜잭션에 함께 묶인다. */
public interface CertificationFinalizeTargets {

	/** 챌린지를 잠그고, 진행 중이면 FINALIZING으로 바꾼다. 이미 FINALIZING이면 그대로, 그 밖의 상태면 예외. */
	void lockForFinalizing(long challengeId, LocalDate today);

	/** 남은 ACTIVE 참가자를 COMPLETED로 바꾼다. */
	void completeActive(long challengeId, Instant now);

	/** participation_result를 저장한다. 이미 있는 참가자는 건너뛴다. */
	void recordResults(List<Result> results, Instant finalizedAt);

	/** 챌린지 FINALIZING → ENDED. */
	void end(long challengeId);

	record Result(long participationId, int approvedCount, int totalCount, int currentStreak, int maxStreak,
			boolean completed) {}
}
