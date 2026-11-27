package com.morak.feature.certification.infrastructure.scheduling;

import com.morak.feature.certification.application.service.AutoApproveCertificationsService;
import com.morak.feature.certification.application.service.ExpelFailedParticipantsService;
import com.morak.feature.certification.application.service.FinalizeChallengesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 12:00 KST 정오 자동 승인·판정 경고(위키 §4.9 ⑤)와 12:05 퇴장 판정(⑥)·결과 확정(⑧). ⑤ → ⑥ → ⑧ 순서로 끝나야 한다.
 * cron 값이 '-'면 실행하지 않는다.
 */
@Component
public class CertificationNoonScheduler {

	private static final Logger log = LoggerFactory.getLogger(CertificationNoonScheduler.class);

	private final AutoApproveCertificationsService autoApprove;
	private final ExpelFailedParticipantsService expel;
	private final FinalizeChallengesService finalizeChallenges;

	public CertificationNoonScheduler(AutoApproveCertificationsService autoApprove,
			ExpelFailedParticipantsService expel, FinalizeChallengesService finalizeChallenges) {
		this.autoApprove = autoApprove;
		this.expel = expel;
		this.finalizeChallenges = finalizeChallenges;
	}

	@Scheduled(cron = "${morak.certification.noon.cron:0 0 12 * * *}", zone = "Asia/Seoul")
	public void runNoon() {
		autoApprove.run();
	}

	/**
	 * 12:05 퇴장 판정(⑥) 뒤 결과 확정(⑧). 배치 스케줄러는 스레드 하나라({@link com.morak.common.config.SchedulingConfig}) 정오 작업이 길어져도 그 작업이 끝난 뒤에 돈다. 퇴장 판정이
	 * 실패해도 결과 확정은 돈다. TODO(D-47): ⑧을 ⑥ 바로 뒤에 붙이는 것은 제안이다.
	 */
	@Scheduled(cron = "${morak.certification.expel.cron:0 5 12 * * *}", zone = "Asia/Seoul")
	public void runExpel() {
		try {
			expel.run();
		} catch (RuntimeException exception) {
			log.error("퇴장 판정 실패", exception);
		}
		try {
			finalizeChallenges.run();
		} catch (RuntimeException exception) {
			log.error("결과 확정 실패", exception);
		}
	}
}
