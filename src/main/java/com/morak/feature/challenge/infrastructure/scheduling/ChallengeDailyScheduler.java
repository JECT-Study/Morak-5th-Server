package com.morak.feature.challenge.infrastructure.scheduling;

import com.morak.feature.challenge.application.service.StartDueChallengesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 00:00 KST 챌린지 배치(위키 §4.9). 같은 00:00 배치에서 시작(충족) 검사를 자동 폐기보다 먼저 해야 하므로(§4.2)
 * 두 작업을 별도 {@code @Scheduled}로 나누지 않고 이 메서드 하나에서 순서대로 부른다. 한 단계가 실패해도 다음 단계는 돈다.
 * cron 값이 '-'면 실행하지 않는다.
 */
@Component
public class ChallengeDailyScheduler {

	private static final Logger log = LoggerFactory.getLogger(ChallengeDailyScheduler.class);

	private final StartDueChallengesService startDueChallenges;

	public ChallengeDailyScheduler(StartDueChallengesService startDueChallenges) {
		this.startDueChallenges = startDueChallenges;
	}

	@Scheduled(cron = "${morak.challenge.daily.cron:0 0 0 * * *}", zone = "Asia/Seoul")
	public void runDaily() {
		// ② 시작 처리
		try {
			startDueChallenges.run();
		} catch (RuntimeException exception) {
			log.error("챌린지 시작 처리 배치 실패", exception);
		}
		// TODO(D-28): ③ 자동 폐기(DiscardChallengeTransaction 재사용, 알림 #7)를 여기서 시작 처리 다음에 호출한다. 별도 작업.
	}
}
