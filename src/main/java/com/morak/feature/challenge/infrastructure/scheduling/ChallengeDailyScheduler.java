package com.morak.feature.challenge.infrastructure.scheduling;

import com.morak.feature.challenge.application.service.DiscardExpiredChallengesService;
import com.morak.feature.challenge.application.service.StartDueChallengesService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 00:00 KST 챌린지 배치(위키 §4.9). 같은 00:00 배치에서 시작(충족) 검사를 자동 폐기보다 먼저 해야 하므로(§4.2)
 * 두 작업을 별도 {@code @Scheduled}로 나누지 않고 이 메서드 하나에서 ② 시작 처리 → ③ 자동 폐기 순서로 부른다.
 * 오늘 날짜는 한 번만 계산해 두 단계가 같은 날짜를 보게 한다. 한 단계가 실패해도 다음 단계는 돈다.
 * cron 값이 '-'면 실행하지 않는다.
 */
@Component
public class ChallengeDailyScheduler {

	private static final Logger log = LoggerFactory.getLogger(ChallengeDailyScheduler.class);
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final StartDueChallengesService startDueChallenges;
	private final DiscardExpiredChallengesService discardExpiredChallenges;
	private final Clock clock;

	public ChallengeDailyScheduler(StartDueChallengesService startDueChallenges,
			DiscardExpiredChallengesService discardExpiredChallenges, Clock clock) {
		this.startDueChallenges = startDueChallenges;
		this.discardExpiredChallenges = discardExpiredChallenges;
		this.clock = clock;
	}

	@Scheduled(cron = "${morak.challenge.daily.cron:0 0 0 * * *}", zone = "Asia/Seoul")
	public void runDaily() {
		LocalDate today = LocalDate.ofInstant(clock.instant(), KST);
		// ② 시작 처리
		try {
			startDueChallenges.runFor(today);
		} catch (RuntimeException exception) {
			log.error("챌린지 시작 처리 배치 실패: today={}", today, exception);
		}
		// ③ 자동 폐기. ②가 실패했어도 돈다. 마감 전에 인원이 찬 챌린지는 폐기하지 않으므로(D-30) 시작할 챌린지를 지우지 않는다.
		try {
			discardExpiredChallenges.runFor(today);
		} catch (RuntimeException exception) {
			log.error("챌린지 자동 폐기 배치 실패: today={}", today, exception);
		}
	}
}
