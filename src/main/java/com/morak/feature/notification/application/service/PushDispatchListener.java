package com.morak.feature.notification.application.service;

import com.morak.feature.notification.application.dto.NotificationSavedEvent;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림을 저장한 트랜잭션이 커밋된 뒤에, 요청 스레드와 별개의 스레드에서 푸시를 보낸다.
 * 호출한 도메인의 응답이 외부 푸시 서비스 때문에 느려지거나 실패하지 않게 하기 위해서다.
 * 롤백되면 이벤트가 사라져 푸시도 나가지 않는다.
 */
@Component
@ConditionalOnProperty(name = "morak.push.expo.enabled", havingValue = "true")
class PushDispatchListener {

	private static final Logger log = LoggerFactory.getLogger(PushDispatchListener.class);
	private static final int THREADS = 4;
	private static final int QUEUE_CAPACITY = 500;

	private final PushDispatchService dispatchService;
	private final ExecutorService executor = new ThreadPoolExecutor(
			THREADS,
			THREADS,
			30,
			TimeUnit.SECONDS,
			new LinkedBlockingQueue<>(QUEUE_CAPACITY),
			namedDaemonThreads("push-send-"),
			(task, pool) -> log.warn("푸시 발송 대기열이 가득 차 이번 푸시를 건너뜀"));

	PushDispatchListener(PushDispatchService dispatchService) {
		this.dispatchService = dispatchService;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onNotificationSaved(NotificationSavedEvent event) {
		executor.execute(() -> {
			try {
				dispatchService.dispatch(event);
			} catch (RuntimeException exception) {
				log.warn(
						"푸시 발송 중 예기치 못한 오류 type={} recipientUserId={} cause={}",
						event.type(),
						event.recipientUserId(),
						exception.toString());
			}
		});
	}

	@PreDestroy
	void shutdown() {
		executor.shutdown();
		try {
			if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
				executor.shutdownNow();
			}
		} catch (InterruptedException exception) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	private static ThreadFactory namedDaemonThreads(String prefix) {
		AtomicInteger count = new AtomicInteger();
		return runnable -> {
			Thread thread = new Thread(runnable, prefix + count.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		};
	}
}
