package com.morak.feature.challenge.application.service;

import com.morak.feature.challenge.application.service.StartChallengeTransaction.Outcome;
import com.morak.feature.challenge.domain.repository.ChallengeRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 시작일이 된 챌린지를 평가하는 일일 배치(위키 §4.9 ②). 몇 번 돌려도 결과가 같다.
 * 챌린지마다 {@link StartChallengeTransaction}을 따로 호출하므로 한 건이 실패해도 그 건만 롤백되고 나머지는 계속 처리한다.
 * 이 클래스에는 트랜잭션을 걸지 않는다(걸면 한 건 실패가 전체를 되돌린다).
 *
 * <p>미달로 시작 보류에 남는 챌린지는 계속 후보이므로 offset이 아니라 id 기준으로 다음 페이지를 읽는다.
 * TODO(D-29): 지난 날짜의 모집 중 챌린지도 처리하는 누락 보정과 페이지 크기(100)는 제안이다.
 */
@Service
public class StartDueChallengesService {

	private static final Logger log = LoggerFactory.getLogger(StartDueChallengesService.class);
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	static final int PAGE_SIZE = 100;

	private final ChallengeRepository challenges;
	private final StartChallengeTransaction transaction;
	private final Clock clock;
	private final int pageSize;

	@Autowired
	public StartDueChallengesService(ChallengeRepository challenges, StartChallengeTransaction transaction, Clock clock) {
		this(challenges, transaction, clock, PAGE_SIZE);
	}

	StartDueChallengesService(ChallengeRepository challenges, StartChallengeTransaction transaction, Clock clock,
			int pageSize) {
		this.challenges = challenges;
		this.transaction = transaction;
		this.clock = clock;
		this.pageSize = pageSize;
	}

	/** 지금 시각의 KST 날짜로 실행한다. */
	public Summary run() {
		return runFor(LocalDate.ofInstant(clock.instant(), KST));
	}

	/** 주어진 KST 날짜를 오늘로 보고 실행한다. */
	public Summary runFor(LocalDate today) {
		int started = 0;
		int held = 0;
		int unchanged = 0;
		int failed = 0;
		long afterId = 0;
		while (true) {
			List<Long> ids = challenges.findStartDueIds(today, afterId, pageSize);
			for (long challengeId : ids) {
				try {
					Outcome outcome = transaction.process(challengeId, today);
					switch (outcome) {
						case STARTED -> started++;
						case HELD -> held++;
						case UNCHANGED -> unchanged++;
					}
				} catch (RuntimeException exception) {
					failed++;
					log.error("챌린지 시작 처리 실패: challengeId={}, today={}", challengeId, today, exception);
				}
			}
			if (ids.size() < pageSize) {
				break;
			}
			afterId = ids.get(ids.size() - 1);
		}
		Summary summary = new Summary(started, held, unchanged, failed);
		if (failed > 0) {
			log.error("챌린지 시작 처리 완료(실패 있음): today={}, {}", today, summary);
		} else {
			log.info("챌린지 시작 처리 완료: today={}, {}", today, summary);
		}
		return summary;
	}

	public record Summary(int started, int held, int unchanged, int failed) {}
}
