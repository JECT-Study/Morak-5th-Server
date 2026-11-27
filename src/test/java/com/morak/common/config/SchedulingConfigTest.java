package com.morak.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class SchedulingConfigTest {

	@Test
	void runsScheduledJobsOnOneBatchThreadEvenWhenAnotherTaskSchedulerBeanExists() {
		new ApplicationContextRunner()
				.withUserConfiguration(SchedulingConfig.class, OtherScheduler.class, Jobs.class)
				.run(context -> {
					Jobs jobs = context.getBean(Jobs.class);
					assertTrue(jobs.ran.await(5, TimeUnit.SECONDS));
					assertEquals(Set.of(SchedulingConfig.THREAD_NAME_PREFIX + "1"), jobs.threads);
				});
	}

	/** WebSocket 브로커 스케줄러처럼 여러 스레드를 가진 다른 TaskScheduler 빈. */
	@Configuration(proxyBeanMethods = false)
	static class OtherScheduler {
		@Bean
		TaskScheduler messageBrokerTaskScheduler() {
			ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
			scheduler.setPoolSize(4);
			scheduler.setThreadNamePrefix("other-");
			return scheduler;
		}
	}

	static class Jobs {
		final Set<String> threads = ConcurrentHashMap.newKeySet();
		final CountDownLatch ran = new CountDownLatch(6);

		@Scheduled(fixedRate = 10)
		void first() {
			record();
		}

		@Scheduled(fixedRate = 10)
		void second() {
			record();
		}

		private void record() {
			threads.add(Thread.currentThread().getName());
			ran.countDown();
		}
	}
}
