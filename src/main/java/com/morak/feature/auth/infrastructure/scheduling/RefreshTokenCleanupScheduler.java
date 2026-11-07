package com.morak.feature.auth.infrastructure.scheduling;

import com.morak.feature.auth.application.service.RefreshTokenCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class RefreshTokenCleanupScheduler {
	private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupScheduler.class);
	private final RefreshTokenCleanupService cleanupService;

	public RefreshTokenCleanupScheduler(RefreshTokenCleanupService cleanupService) {
		this.cleanupService = cleanupService;
	}

	/** Runs expired-token retention cleanup daily; a cron value of '-' disables execution. */
	@Scheduled(cron = "${morak.auth.refresh-token-cleanup.cron:0 0 3 * * *}", zone = "Asia/Seoul")
	public void cleanup() {
		log.info("Expired refresh token cleanup deleted {} rows", cleanupService.cleanup());
	}
}
