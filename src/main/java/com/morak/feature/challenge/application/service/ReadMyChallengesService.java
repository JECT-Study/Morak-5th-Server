package com.morak.feature.challenge.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.challenge.application.dto.query.MyChallengeFilter;
import com.morak.feature.challenge.application.dto.result.MyChallengeItem;
import com.morak.feature.challenge.application.dto.result.MyChallengeItem.StartHeld;
import com.morak.feature.challenge.application.dto.result.MyChallengePage;
import com.morak.feature.challenge.application.port.out.ChallengeCertificationReader;
import com.morak.feature.challenge.application.port.out.ChallengeCertificationReader.CertificationStats;
import com.morak.feature.challenge.application.port.out.ChallengeParticipationReader;
import com.morak.feature.challenge.application.port.out.ChallengeParticipationReader.MyParticipationRow;
import com.morak.feature.challenge.application.query.ChallengeReadQuery;
import com.morak.feature.challenge.application.query.ChallengeReadQuery.ChallengeDetail;
import com.morak.feature.challenge.domain.enums.ChallengeStatus;
import com.morak.feature.challenge.domain.enums.ChallengeVisibility;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 내 챌린지(홈·보관함) 목록. 참가 정보는 참가 도메인, 인증 집계는 인증 도메인에서 포트로 읽는다. */
@Service
public class ReadMyChallengesService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final int MAX_PAGE_SIZE = 100;

	// TODO(D-22): 홈·보관함 상태 분류는 PM·프론트 확정 전 제안값이다.
	private static final Set<String> HOME_PARTICIPATION_STATUSES = Set.of("ACTIVE");
	private static final Set<ChallengeStatus> HOME_CHALLENGE_STATUSES = EnumSet.of(
			ChallengeStatus.RECRUITING, ChallengeStatus.START_HELD, ChallengeStatus.IN_PROGRESS, ChallengeStatus.FINALIZING);
	private static final Set<String> ARCHIVE_PARTICIPATION_STATUSES = Set.of("EXPELLED", "COMPLETED", "EARLY_ENDED", "DISCARDED");

	private final ChallengeParticipationReader participations;
	private final ChallengeReadQuery challenges;
	private final ChallengeCertificationReader certifications;
	private final ChallengeCoverImages coverImages;
	private final Clock clock;

	public ReadMyChallengesService(ChallengeParticipationReader participations, ChallengeReadQuery challenges,
			ChallengeCertificationReader certifications, ChallengeCoverImages coverImages, Clock clock) {
		this.participations = participations;
		this.challenges = challenges;
		this.certifications = certifications;
		this.coverImages = coverImages;
		this.clock = clock;
	}

	public MyChallengePage list(long userId, MyChallengeFilter filter, Long cursor, int size) {
		return filter == MyChallengeFilter.ARCHIVE ? archive(userId, cursor, size) : home(userId);
	}

	private MyChallengePage home(long userId) {
		List<MyParticipationRow> rows = participations.findMine(userId, HOME_PARTICIPATION_STATUSES);
		List<MyChallengeItem> items = toItems(rows, HOME_CHALLENGE_STATUSES);
		return new MyChallengePage(items, null, false);
	}

	private MyChallengePage archive(long userId, Long cursor, int size) {
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
		}
		List<MyParticipationRow> rows = participations.findMine(userId, ARCHIVE_PARTICIPATION_STATUSES, cursor, size + 1);
		boolean hasNext = rows.size() > size;
		if (hasNext) {
			rows = new ArrayList<>(rows.subList(0, size));
		}
		Long nextCursor = hasNext ? rows.getLast().participationId() : null;
		return new MyChallengePage(toItems(rows, EnumSet.allOf(ChallengeStatus.class)), nextCursor, hasNext);
	}

	private List<MyChallengeItem> toItems(List<MyParticipationRow> rows, Set<ChallengeStatus> allowedStatuses) {
		if (rows.isEmpty()) {
			return List.of();
		}
		Map<Long, ChallengeDetail> challengeById = challenges.findByIds(
				rows.stream().map(MyParticipationRow::challengeId).distinct().toList())
				.stream().collect(Collectors.toMap(ChallengeDetail::id, Function.identity()));
		// 삭제된 챌린지와 이 목록에 맞지 않는 챌린지 상태는 뺀다.
		List<MyParticipationRow> visible = rows.stream()
				.filter(row -> {
					ChallengeDetail challenge = challengeById.get(row.challengeId());
					return challenge != null && allowedStatuses.contains(challenge.status());
				})
				.toList();
		if (visible.isEmpty()) {
			return List.of();
		}
		LocalDate today = LocalDate.now(clock.withZone(KST));
		Map<Long, CertificationStats> stats = certifications.findStats(
				visible.stream().map(MyParticipationRow::participationId).toList(), today);
		Map<String, String> urls = coverImages.urls(visible.stream()
				.map(row -> challengeById.get(row.challengeId()).coverImageKey())
				.filter(Objects::nonNull).distinct().toList());
		return visible.stream()
				.map(row -> toItem(row, challengeById.get(row.challengeId()), stats.get(row.participationId()), urls, today))
				.toList();
	}

	private MyChallengeItem toItem(MyParticipationRow row, ChallengeDetail challenge, CertificationStats stats,
			Map<String, String> urls, LocalDate today) {
		String key = challenge.coverImageKey();
		return new MyChallengeItem(row.participationId(), challenge.id(), challenge.title(), challenge.category(),
				key == null ? null : urls.get(key), challenge.startDate(), challenge.endDate(), challenge.pointAmount(),
				row.role(), row.status(), challenge.status().name(), challenge.currentCount(), challenge.capacity(),
				progressDay(challenge, today), startHeld(challenge),
				stats == null ? null : stats.teamAchievementRate(),
				stats == null ? null : stats.todayCertifiedCount(),
				stats == null ? null : stats.todayTargetCount(),
				stats == null ? null : stats.myTodayStatus(),
				stats == null ? null : stats.pendingJudgeCount());
	}

	/** 진행 일차(시작일 = 1일차). 진행 중·정산 대기에만 값이 있고 1일차~마지막 날 사이로 제한한다. */
	static Integer progressDay(ChallengeDetail challenge, LocalDate today) {
		if (challenge.status() != ChallengeStatus.IN_PROGRESS && challenge.status() != ChallengeStatus.FINALIZING) {
			return null;
		}
		long totalDays = ChronoUnit.DAYS.between(challenge.startDate(), challenge.endDate()) + 1;
		long day = ChronoUnit.DAYS.between(challenge.startDate(), today) + 1;
		return (int) Math.max(1, Math.min(day, totalDays));
	}

	/** 시작 보류 안내. 추가 모집 마감은 예정 시작일 + 2일 23:59:59 KST다(위키 §4.2). */
	static StartHeld startHeld(ChallengeDetail challenge) {
		if (challenge.status() != ChallengeStatus.START_HELD) {
			return null;
		}
		int minCount = challenge.visibility() == ChallengeVisibility.PUBLIC ? 3 : 2;
		OffsetDateTime deadline = challenge.startDate().plusDays(2).atTime(LocalTime.of(23, 59, 59)).atZone(KST)
				.toOffsetDateTime();
		return new StartHeld(challenge.currentCount(), minCount, deadline);
	}
}
