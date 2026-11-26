package com.morak.feature.certification.application.service;

import com.morak.feature.certification.application.port.out.CertificationCardStore;
import com.morak.feature.certification.application.port.out.CertificationCardStore.CardRow;
import com.morak.feature.certification.application.port.out.CertificationCardStore.PhotoRow;
import com.morak.feature.certification.application.port.out.CertificationImageUrls;
import com.morak.feature.certification.application.query.CertificationCardQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 홈 카드 인증 집계(위키 §4.5, D-23 ⑤~⑦). 카드 수와 상관없이 쿼리 두 번으로 읽는다.
 *
 * <p>TODO(D-44): 오늘 사진은 승인된 인증만 넣는다(판정 전 사진 노출은 PM 확인 전). 판정 대기 수는 날짜와 상관없이 PENDING 전체를 센다.
 * TODO(O-23): 면제일은 정의가 없어 의무 횟수에서 빼지 않는다.
 */
@Service
public class ReadCertificationCardsService implements CertificationCardQuery {

	private final CertificationCardStore store;
	private final CertificationImageUrls imageUrls;

	public ReadCertificationCardsService(CertificationCardStore store, CertificationImageUrls imageUrls) {
		this.store = store;
		this.imageUrls = imageUrls;
	}

	@Override
	public Map<Long, CardStats> findCardStats(Collection<Long> participationIds, LocalDate today) {
		if (participationIds.isEmpty()) {
			return Map.of();
		}
		List<CardRow> cards = store.findCards(participationIds, today);
		List<Long> openChallenges =
				cards.stream().filter(CardRow::todayOpen).map(CardRow::challengeId).distinct().toList();
		List<PhotoRow> photos = openChallenges.isEmpty() ? List.of() : store.findApprovedPhotos(openChallenges, today);
		Map<String, String> urls = imageUrls.resolveByKey(
				photos.stream().map(PhotoRow::imageKey).filter(Objects::nonNull).toList());
		Map<Long, List<TodayPhoto>> photosByChallenge = photos.stream().collect(Collectors.groupingBy(
				PhotoRow::challengeId,
				Collectors.mapping(row -> new TodayPhoto(row.userId(),
						row.imageKey() == null ? null : urls.get(row.imageKey()), row.submittedAt()),
						Collectors.toList())));

		Map<Long, CardStats> result = new HashMap<>();
		for (CardRow card : cards) {
			TodayTeam todayTeam = card.todayOpen()
					? new TodayTeam(card.todayCertified(), card.todayTarget(),
							photosByChallenge.getOrDefault(card.challengeId(), List.of()))
					: null;
			result.put(card.participationId(), new CardStats(teamAchievementRate(card.teamTarget(), card.teamFailed()),
					todayTeam, card.myTodayStatus(), card.pendingJudgeCount()));
		}
		return result;
	}

	/** (대상 − 실패) ÷ 대상 × 100을 소수 첫째 자리 half-up으로(위키 §3, §4.5). 정수 연산. 대상이 없으면 null. */
	static BigDecimal teamAchievementRate(Long target, long failed) {
		if (target == null || target <= 0) {
			return null;
		}
		long tenths = (2000 * (target - failed) + target) / (2 * target);
		return BigDecimal.valueOf(tenths, 1);
	}
}
