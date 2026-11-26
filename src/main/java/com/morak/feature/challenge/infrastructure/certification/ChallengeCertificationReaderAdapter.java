package com.morak.feature.challenge.infrastructure.certification;

import com.morak.feature.certification.application.query.CertificationCardQuery;
import com.morak.feature.certification.application.query.CertificationCardQuery.CardStats;
import com.morak.feature.challenge.application.port.out.ChallengeCertificationReader;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 홈 카드 인증 집계 포트를 인증 모듈의 공개 조회 API로 연결한다. */
@Component
class ChallengeCertificationReaderAdapter implements ChallengeCertificationReader {

	private final CertificationCardQuery cardQuery;

	ChallengeCertificationReaderAdapter(CertificationCardQuery cardQuery) {
		this.cardQuery = cardQuery;
	}

	@Override
	public Map<Long, CertificationStats> findStats(Collection<Long> participationIds, LocalDate today) {
		Map<Long, CertificationStats> result = new HashMap<>();
		cardQuery.findCardStats(participationIds, today).forEach((id, stats) -> result.put(id, toStats(stats)));
		return result;
	}

	private static CertificationStats toStats(CardStats stats) {
		CertificationCardQuery.TodayTeam team = stats.todayTeam();
		TodayTeam todayTeam = team == null ? null : new TodayTeam(team.certifiedCount(), team.targetCount(),
				team.photos().stream()
						.map(photo -> new TodayPhoto(photo.userId(), photo.thumbnailUrl(), photo.submittedAt()))
						.toList());
		return new CertificationStats(stats.teamAchievementRate(), todayTeam, stats.myTodayStatus(),
				stats.pendingJudgeCount());
	}
}
