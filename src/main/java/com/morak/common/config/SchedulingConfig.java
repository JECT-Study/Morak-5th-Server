package com.morak.common.config;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * 애플리케이션 전체의 {@code @Scheduled} 작업을 켠다. 스케줄러는 각 도메인의 infrastructure/scheduling에 둔다.
 *
 * <p>배치는 전용 스레드 하나에서 차례로 돈다. 그래서 12:00 정오 자동 승인이 길어지면 12:05 퇴장 판정은 그 뒤에 시작한다(위키 §4.9 순서).
 * 따로 지정하지 않으면 WebSocket 브로커의 {@code messageBrokerTaskScheduler}(여러 스레드)를 같이 써서 순서가 보장되지 않는다.
 * 빈으로 등록하지 않아 WebSocket 쪽 {@code TaskScheduler} 주입과 겹치지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer, DisposableBean {

	static final String THREAD_NAME_PREFIX = "batch-";

	private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

	@Override
	public void configureTasks(ScheduledTaskRegistrar registrar) {
		scheduler.setPoolSize(1);
		scheduler.setThreadNamePrefix(THREAD_NAME_PREFIX);
		scheduler.initialize();
		registrar.setTaskScheduler(scheduler);
	}

	@Override
	public void destroy() {
		scheduler.shutdown();
	}
}
