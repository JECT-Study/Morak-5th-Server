package com.morak.feature.challenge.application.query;

import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import com.morak.feature.challenge.domain.enums.ChallengeStatus;
import com.morak.feature.challenge.domain.enums.ChallengeVisibility;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 챌린지 탐색과 상세 응답에 필요한 조회 포트. */
public interface ChallengeReadQuery {

	List<ChallengeSummary> findRecruitingPublic(ChallengeCategory category, String keyword, Long cursor, int size);

	Optional<ChallengeDetail> findById(long challengeId);

	record ChallengeSummary(long id, String title, String description, ChallengeCategory category,
			LocalDate startDate, LocalDate endDate,
			int capacity, int currentCount, int pointAmount, String coverImageKey) {}

	record ChallengeDetail(long id, long ownerUserId, String title, String description, ChallengeVisibility visibility, ChallengeCategory category,
			String purpose, String commonMission, String verificationRules, String expectations, ChallengeStatus status,
			LocalDate startDate, LocalDate endDate, int capacity, int currentCount, int pointAmount, String coverImageKey) {}
}
