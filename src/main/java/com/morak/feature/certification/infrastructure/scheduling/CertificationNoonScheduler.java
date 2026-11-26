package com.morak.feature.certification.infrastructure.scheduling;

import com.morak.feature.certification.application.service.AutoApproveCertificationsService;
import com.morak.feature.certification.application.service.ExpelFailedParticipantsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 12:00 KST 정오 자동 승인·판정 경고(위키 §4.9 ⑤)와 12:05 퇴장 판정(⑥). ⑤가 ⑥보다 먼저 끝나야 한다. cron 값이 '-'면 실행하지 않는다.
 */
@Component
public class CertificationNoonScheduler {

	private final AutoApproveCertificationsService autoApprove;
	private final ExpelFailedParticipantsService expel;

	public CertificationNoonScheduler(AutoApproveCertificationsService autoApprove,
			ExpelFailedParticipantsService expel) {
		this.autoApprove = autoApprove;
		this.expel = expel;
	}

	@Scheduled(cron = "${morak.certification.noon.cron:0 0 12 * * *}", zone = "Asia/Seoul")
	public void runNoon() {
		autoApprove.run();
	}

	/** 12:05 퇴장 판정(⑥). 기본 스케줄러는 스레드 하나라 정오 작업이 길어져도 그 작업이 끝난 뒤에 돈다. */
	@Scheduled(cron = "${morak.certification.expel.cron:0 5 12 * * *}", zone = "Asia/Seoul")
	public void runExpel() {
		expel.run();
	}
}
