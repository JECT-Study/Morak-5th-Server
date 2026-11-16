package com.morak.feature.challenge.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.challenge.application.dto.query.ListChallengesQuery;
import com.morak.feature.challenge.application.dto.result.CategoryItem;
import com.morak.feature.challenge.application.dto.result.ChallengeListItem;
import com.morak.feature.challenge.application.dto.result.ChallengePage;
import com.morak.feature.challenge.application.dto.result.ChallengeView;
import com.morak.feature.challenge.application.port.out.ChallengeParticipationReader;
import com.morak.feature.challenge.application.query.ChallengeReadQuery;
import com.morak.feature.challenge.application.query.ChallengeReadQuery.ChallengeDetail;
import com.morak.feature.challenge.application.query.ChallengeReadQuery.ChallengeSummary;
import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ReadChallengeService {

	private static final int PARTICIPATION_LIMIT = 5;
	private static final int MAX_PAGE_SIZE = 100;
	private final ChallengeReadQuery challenges;
	private final ChallengeParticipationReader participations;
	private final ChallengeCoverImages coverImages;

	public ReadChallengeService(ChallengeReadQuery challenges, ChallengeParticipationReader participations,
			ChallengeCoverImages coverImages) {
		this.challenges = challenges;
		this.participations = participations;
		this.coverImages = coverImages;
	}

	public ChallengePage list(ListChallengesQuery query) {
		int size = query.size();
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
		}
		List<ChallengeSummary> rows = challenges.findRecruitingPublic(query.category(), query.keyword(), query.cursor(), size + 1);
		boolean hasNext = rows.size() > size;
		if (hasNext) rows = new ArrayList<>(rows.subList(0, size));
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
		if ("PRIVATE".equals(challenge.visibility())
				&& (userId == null || (userId != challenge.ownerUserId() && myStatus.isEmpty()))) {
			throw new BusinessException(ChallengeErrorCode.PRIVATE_CHALLENGE_FORBIDDEN);
		}
		boolean canApply = userId != null
				&& ("RECRUITING".equals(challenge.status()) || "START_HELD".equals(challenge.status()))
				&& challenge.currentCount() < challenge.capacity()
				&& myStatus.isEmpty()
				&& participations.countOngoing(userId) < PARTICIPATION_LIMIT;
		String key = challenge.coverImageKey();
		String coverImageUrl = key == null ? null : coverImages.urls(List.of(key)).get(key);
		return new ChallengeView(challenge.id(), challenge.title(), challenge.description(), challenge.visibility(), challenge.category(),
				challenge.purpose(), challenge.commonMission(), challenge.verificationRules(), challenge.expectations(),
				challenge.status(), challenge.startDate(), challenge.endDate(), challenge.capacity(), challenge.currentCount(),
				challenge.pointAmount(), myStatus.orElse(null), canApply, coverImageUrl);
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
