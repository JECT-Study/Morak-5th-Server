package com.morak.feature.notification.infrastructure.push;

import com.morak.feature.notification.application.dto.PushReceipt;
import com.morak.feature.notification.application.port.out.PushGateway;
import com.morak.feature.notification.application.port.out.PushReceiptScheduler;
import com.morak.feature.notification.application.service.PushTokenService;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 푸시를 보내고 일정 시간이 지난 뒤 영수증을 확인해, 더 이상 푸시를 받을 수 없는 기기의 토큰을 지운다.
 *
 * 확인할 목록은 메모리에만 있다. DB 테이블을 새로 만들지 않기 위해서이고, 서버가 재시작되면 그 사이 대기 중이던
 * 확인은 사라진다. 놓친 토큰은 다음에 그 토큰으로 보낼 때 다시 걸러진다.
 */
@Component
@ConditionalOnProperty(name = "morak.push.expo.enabled", havingValue = "true")
class PushReceiptChecker implements PushReceiptScheduler {

	private static final Logger log = LoggerFactory.getLogger(PushReceiptChecker.class);

	private final PushGateway gateway;
	private final PushTokenService pushTokenService;
	private final Duration delay;
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "push-receipt-1");
		thread.setDaemon(true);
		return thread;
	});

	PushReceiptChecker(
			PushGateway gateway,
			PushTokenService pushTokenService,
			@Value("${morak.push.expo.receipt-delay:PT15M}") Duration delay) {
		this.gateway = gateway;
		this.pushTokenService = pushTokenService;
		this.delay = delay;
	}

	@Override
	public void schedule(Map<String, String> ticketToToken) {
		if (ticketToToken.isEmpty()) {
			return;
		}
		Map<String, String> copy = Map.copyOf(ticketToToken);
		executor.schedule(() -> check(copy), delay.toMillis(), TimeUnit.MILLISECONDS);
	}

	/** 영수증을 확인해 DeviceNotRegistered인 토큰을 지운다. 어떤 오류가 나도 예외를 던지지 않는다. */
	void check(Map<String, String> ticketToToken) {
		try {
			Set<String> unusableTokens = new LinkedHashSet<>();
			List<String> ids = new ArrayList<>(ticketToToken.keySet());
			int size = PushGateway.MAX_IDS_PER_RECEIPT_REQUEST;
			for (int from = 0; from < ids.size(); from += size) {
				List<String> batch = ids.subList(from, Math.min(from + size, ids.size()));
				Map<String, PushReceipt> receipts = gateway.getReceipts(batch);
				receipts.forEach((id, receipt) -> {
					if (!receipt.isOk() && PushGateway.DEVICE_NOT_REGISTERED.equals(receipt.error())) {
						unusableTokens.add(ticketToToken.get(id));
					} else if (!receipt.isOk()) {
						log.warn("푸시 영수증 실패 error={}", receipt.error());
					}
				});
			}
			unusableTokens.remove(null);
			if (!unusableTokens.isEmpty()) {
				int removed = pushTokenService.removeUnusable(unusableTokens);
				log.info("영수증 확인으로 사용할 수 없는 푸시 토큰 삭제 removed={}", removed);
			}
		} catch (RuntimeException exception) {
			log.warn("푸시 영수증 확인 실패 tickets={} cause={}", ticketToToken.size(), exception.toString());
		}
	}

	@PreDestroy
	void shutdown() {
		executor.shutdownNow();
	}
}
