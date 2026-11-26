package com.morak.feature.certification.infrastructure.scheduling;

import com.morak.feature.certification.application.service.AutoApproveCertificationsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 12:00 KST 정오 자동 승인·판정 경고(위키 §4.9 ⑤). 12:05 퇴장 판정(⑥)보다 먼저 끝나야 한다. cron 값이 '-'면 실행하지 않는다.
 */
@Component
public class CertificationNoonScheduler {

	private final AutoApproveCertificationsService autoApprove;

	public CertificationNoonScheduler(AutoApproveCertificationsService autoApprove) {
		this.autoApprove = autoApprove;
	}

	@Scheduled(cron = "${morak.certification.noon.cron:0 0 12 * * *}", zone = "Asia/Seoul")
	public void runNoon() {
		autoApprove.run();
	}
}
