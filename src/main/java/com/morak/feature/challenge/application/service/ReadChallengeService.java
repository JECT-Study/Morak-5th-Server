package com.morak.feature.challenge.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.common.exception.RateLimitedException;
import com.morak.feature.challenge.application.dto.query.ListChallengesQuery;
import com.morak.feature.challenge.application.dto.result.CategoryItem;
import com.morak.feature.challenge.application.dto.result.ChallengeListItem;
import com.morak.feature.challenge.application.dto.result.ChallengePage;
import com.morak.feature.challenge.application.dto.result.ChallengeView;
import com.morak.feature.challenge.application.port.out.ChallengeParticipationReader;
import com.morak.feature.challenge.application.port.out.InviteCodeAttemptLimiter;
import com.morak.feature.challenge.application.query.ChallengeReadQuery;
import com.morak.feature.challenge.application.query.ChallengeReadQuery.ChallengeDetail;
import com.morak.feature.challenge.application.query.ChallengeReadQuery.ChallengeSummary;
import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import com.morak.feature.challenge.domain.enums.ChallengeStatus;
import com.morak.feature.challenge.domain.enums.ChallengeVisibility;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import com.morak.feature.challenge.domain.policy.InviteCodePolicy;
import com.morak.feature.challenge.domain.policy.ParticipationLimitPolicy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ReadChallengeService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final Set<ChallengeStatus> ENDED_STATUSES =
			EnumSet.of(ChallengeStatus.FINALIZING, ChallengeStatus.ENDED, ChallengeStatus.DISCARDED);
	private final ChallengeReadQuery challenges;
	private final ChallengeParticipationReader participations;
	private final ChallengeCoverImages coverImages;
	private final InviteCodeAttemptLimiter attemptLimiter;

	public ReadChallengeService(ChallengeReadQuery challenges, ChallengeParticipationReader participations,
			ChallengeCoverImages coverImages, InviteCodeAttemptLimiter attemptLimiter) {
		this.challenges = challenges;
		this.participations = participations;
		this.coverImages = coverImages;
		this.attemptLimiter = attemptLimiter;
	}

	public ChallengePage list(ListChallengesQuery query) {
		int size = query.size();
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
		}
		List<ChallengeSummary> rows = challenges.findRecruitingPublic(query.category(), query.keyword(), query.cursor(), size + 1);
		boolean hasNext = rows.size() > size;
		if (hasNext) {
			rows = new ArrayList<>(rows.subList(0, size));
		}
		Long nextCursor = hasNext ? rows.getLast().id() : null;
		var urls = coverImages.urls(rows.stream().map(ChallengeSummary::coverImageKey).filter(Objects::nonNull).distinct().toList());
		var items = rows.stream().map(row -> new ChallengeListItem(row.id(), row.title(), row.description(), row.category(),
				row.startDate(), row.endDate(), row.capacity(), row.currentCount(), row.pointAmount(),
				row.coverImageKey() == null ? null : urls.get(row.coverImageKey()))).toList();
		return new ChallengePage(items, nextCursor, hasNext);
	}

	public ChallengeView get(long challengeId, Long userId) {
		ChallengeDetail challenge = challenges.findById(challengeId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		Optional<String> myStatus = userId == null ? Optional.empty() : participations.findStatus(challengeId, userId);
		if (challenge.visibility() == ChallengeVisibility.PRIVATE
				&& (userId == null || (userId != challenge.ownerUserId() && myStatus.isEmpty()))) {
			throw new BusinessException(ChallengeErrorCode.PRIVATE_CHALLENGE_FORBIDDEN);
		}
		return toView(challenge, userId, myStatus);
	}

	/**
	 * 초대 코드로 상세에 진입한다(위키 §4.2). 코드를 아는 사용자는 비공개 챌린지도 볼 수 있고, 합류는 하지 않는다.
	 * 종료·폐기된 챌린지는 상세 대신 "이미 종료된 챌린지입니다"로 구분한다. FINALIZING도 종료로 본다.
	 */
	public ChallengeView getByInviteCode(String inviteCode, long userId) {
		// 무차별 대입 방지(D-40): 조회 전에 시도 한 칸을 선점하고, 유효하지 않은 코드만 실패로 센다.
		// 한도를 넘으면 맞는 코드도 잠시 막는다.
		var decision = attemptLimiter.tryAcquire(userId);
		if (!decision.allowed()) {
			throw new RateLimitedException(ChallengeErrorCode.INVITE_CODE_ATTEMPTS_EXCEEDED, decision.retryAfter(),
					"userId=" + userId);
		}
		Optional<ChallengeDetail> found;
		try {
			found = InviteCodePolicy.matchesFormat(inviteCode) ? challenges.findByInviteCode(inviteCode) : Optional.empty();
		} catch (RuntimeException exception) {
			// 조회 자체가 실패한 경우는 사용자의 잘못된 입력이 아니므로 실패로 세지 않는다.
			attemptLimiter.release(userId, false);
			throw exception;
		}
		attemptLimiter.release(userId, found.isEmpty());
		ChallengeDetail challenge = found.orElseThrow(() -> new BusinessException(ChallengeErrorCode.INVALID_INVITE_CODE));
		if (ENDED_STATUSES.contains(challenge.status())) {
			throw new BusinessException(ChallengeErrorCode.ALREADY_ENDED);
		}
		return toView(challenge, userId, participations.findStatus(challenge.id(), userId));
	}

	private ChallengeView toView(ChallengeDetail challenge, Long userId, Optional<String> myStatus) {
		boolean canApply = canApply(challenge, userId, myStatus);
		String key = challenge.coverImageKey();
		String coverImageUrl = key == null ? null : coverImages.urls(List.of(key)).get(key);
		return new ChallengeView(challenge.id(), challenge.title(), challenge.description(), challenge.visibility().name(), challenge.category(),
				challenge.purpose(), challenge.commonMission(), challenge.verificationRules(), challenge.expectations(),
				challenge.status().name(), challenge.startDate(), challenge.endDate(), challenge.capacity(), challenge.currentCount(),
				challenge.pointAmount(), myStatus.orElse(null), canApply, coverImageUrl);
	}

	private boolean canApply(ChallengeDetail challenge, Long userId, Optional<String> myStatus) {
		return userId != null
				&& (challenge.status() == ChallengeStatus.RECRUITING || challenge.status() == ChallengeStatus.START_HELD)
				&& challenge.currentCount() < challenge.capacity()
				&& myStatus.isEmpty()
				&& ParticipationLimitPolicy.canJoinMore(() -> participations.countOngoing(userId));
	}

	public List<CategoryItem> categories() {
		return List.of(
				new CategoryItem(ChallengeCategory.EXERCISE, "운동"),
				new CategoryItem(ChallengeCategory.STUDY, "공부"),
				new CategoryItem(ChallengeCategory.CAREER, "커리어"),
				new CategoryItem(ChallengeCategory.READING, "독서"),
				new CategoryItem(ChallengeCategory.LIFESTYLE, "생활습관"));
		}
}
