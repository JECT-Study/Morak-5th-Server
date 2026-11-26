package com.morak.feature.participation.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.participation.application.dto.result.ChallengeResultView;
import com.morak.feature.participation.application.dto.result.ChallengeResultView.Member;
import com.morak.feature.participation.application.dto.result.ChallengeResultView.Mine;
import com.morak.feature.participation.application.dto.result.ChallengeResultView.Team;
import com.morak.feature.participation.application.port.out.ApplicantProfileReader;
import com.morak.feature.participation.application.port.out.ApplicantProfileReader.ApplicantSource;
import com.morak.feature.participation.application.port.out.ApplicationChallenges;
import com.morak.feature.participation.application.query.ParticipationQuery;
import com.morak.feature.participation.application.query.ParticipationQuery.MyParticipation;
import com.morak.feature.participation.application.query.ParticipationQuery.ParticipantInfo;
import com.morak.feature.participation.application.query.ParticipationQuery.ParticipantResult;
import com.morak.feature.participation.domain.entity.AchievementRate;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 챌린지 종료 후 결과 조회(위키 §4.5). 정산이 확정한 participation_result만 읽고 다시 계산하지 않는다.
 * 팀 달성률만 참가자별 결과의 합으로 구한다. 참가자(참여가 확정된 적이 있는 사람)만 볼 수 있고, 결과 확정 전이면 409다.
 * TODO(D-28): 경로는 API 문서를 따르고, 응답 필드와 오류 코드(403, 409)는 제안이다.
 */
@Service
public class ReadChallengeResultsService {

	/** 참여가 확정된 적이 있는 참가 상태. 결과 행을 가질 수 있는 상태와 같다. */
	private static final Set<String> RESULT_STATUSES = Set.of("ACTIVE", "COMPLETED", "EXPELLED", "EARLY_ENDED");

	private final ApplicationChallenges challenges;
	private final ParticipationQuery participationQuery;
	private final ApplicantProfileReader profiles;

	public ReadChallengeResultsService(
			ApplicationChallenges challenges, ParticipationQuery participationQuery, ApplicantProfileReader profiles) {
		this.challenges = challenges;
		this.participationQuery = participationQuery;
		this.profiles = profiles;
	}

	@Transactional(readOnly = true)
	public Team team(long userId, long challengeId) {
		List<ParticipantResult> results = findFinalizedResults(userId, challengeId).results();
		Set<Long> participationIds = results.stream().map(ParticipantResult::participationId).collect(Collectors.toSet());
		Set<Long> userIds = results.stream().map(ParticipantResult::userId).collect(Collectors.toSet());
		Map<Long, ParticipantInfo> infos = participationQuery.findParticipantsByIds(participationIds);
		Map<Long, ApplicantSource> sources = profiles.findByUserIds(userIds);
		List<Member> members = results.stream()
				.map(result -> toMember(result, infos.get(result.participationId()), sources.get(result.userId())))
				.toList();
		return new Team(approvedSum(results), totalSum(results), teamRate(results), members);
	}

	@Transactional(readOnly = true)
	public Mine mine(long userId, long challengeId) {
		FinalizedResults found = findFinalizedResults(userId, challengeId);
		ParticipantResult mine = found.results().stream()
				.filter(result -> result.participationId() == found.me().participationId())
				.findFirst()
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		return new Mine(
				mine.participationId(),
				mine.approvedCount(),
				mine.totalCount(),
				mine.achievementRate(),
				teamRate(found.results()),
				mine.completed(),
				"EXPELLED".equals(found.me().status()),
				mine.currentStreak(),
				mine.maxStreak(),
				mine.finalizedAt());
	}

	/** 챌린지가 있고, 요청자가 참가자이고, 결과가 확정돼 있어야 한다. */
	private FinalizedResults findFinalizedResults(long userId, long challengeId) {
		challenges.find(challengeId).orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		MyParticipation me = participationQuery.findMyParticipation(challengeId, userId)
				.filter(participation -> RESULT_STATUSES.contains(participation.status()))
				.orElseThrow(() -> new BusinessException(ParticipationErrorCode.NOT_CHALLENGE_PARTICIPANT));
		List<ParticipantResult> results = participationQuery.findResults(challengeId);
		if (results.isEmpty()) {
			throw new BusinessException(ParticipationErrorCode.RESULT_NOT_FINALIZED);
		}
		return new FinalizedResults(me, results);
	}

	private static Member toMember(ParticipantResult result, ParticipantInfo info, ApplicantSource source) {
		return new Member(
				result.participationId(),
				result.userId(),
				source == null ? null : source.nickname(),
				source == null ? null : source.profileImageUrl(),
				result.approvedCount(),
				result.totalCount() - result.approvedCount(),
				result.achievementRate(),
				result.completed(),
				info != null && "EXPELLED".equals(info.status()));
	}

	private static BigDecimal teamRate(List<ParticipantResult> results) {
		return AchievementRate.percent(approvedSum(results), totalSum(results));
	}

	private static int approvedSum(List<ParticipantResult> results) {
		return results.stream().mapToInt(ParticipantResult::approvedCount).sum();
	}

	private static int totalSum(List<ParticipantResult> results) {
		return results.stream().mapToInt(ParticipantResult::totalCount).sum();
	}

	private record FinalizedResults(MyParticipation me, List<ParticipantResult> results) {}
}
