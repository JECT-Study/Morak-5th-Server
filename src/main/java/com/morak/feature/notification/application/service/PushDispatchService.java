package com.morak.feature.notification.application.service;

import com.morak.feature.notification.application.dto.NotificationSavedEvent;
import com.morak.feature.notification.application.dto.PushMessage;
import com.morak.feature.notification.application.dto.PushTicket;
import com.morak.feature.notification.application.port.out.PushGateway;
import com.morak.feature.notification.application.port.out.PushReceiptScheduler;
import com.morak.feature.notification.domain.entity.PushChannel;
import com.morak.feature.notification.domain.repository.PushTokenRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 저장된 알림을 사용자의 기기로 푸시한다. 푸시는 앱 안 알림함의 보조 수단이라 실패해도 예외를 던지지 않고
 * 로그만 남긴다 (알림은 이미 저장되어 있다). 재시도는 하지 않는다.
 */
@Service
@ConditionalOnProperty(name = "morak.push.expo.enabled", havingValue = "true")
public class PushDispatchService {

	private static final Logger log = LoggerFactory.getLogger(PushDispatchService.class);

	private final PushTokenRepository pushTokenRepository;
	private final PushTokenService pushTokenService;
	private final PushGateway gateway;
	private final PushReceiptScheduler receiptScheduler;

	public PushDispatchService(
			PushTokenRepository pushTokenRepository,
			PushTokenService pushTokenService,
			PushGateway gateway,
			PushReceiptScheduler receiptScheduler) {
		this.pushTokenRepository = pushTokenRepository;
		this.pushTokenService = pushTokenService;
		this.gateway = gateway;
		this.receiptScheduler = receiptScheduler;
	}

	public void dispatch(NotificationSavedEvent event) {
		List<String> tokens = pushTokenRepository.findEnabledTokens(event.recipientUserId(), PushChannel.EXPO);
		if (tokens.isEmpty()) {
			return;
		}
		String url = PushLinks.pathOf(event.targetType(), event.targetId());
		List<PushMessage> messages = tokens.stream()
				.map(token -> new PushMessage(token, event.title(), event.body(), url))
				.toList();

		Set<String> unusableTokens = new LinkedHashSet<>();
		Map<String, String> ticketToToken = new LinkedHashMap<>();
		for (List<PushMessage> batch : partition(messages)) {
			List<PushTicket> tickets;
			try {
				tickets = gateway.send(batch);
			} catch (RuntimeException exception) {
				// 토큰 값은 남기지 않는다.
				log.warn(
						"푸시 발송 실패 type={} recipientUserId={} count={} cause={}",
						event.type(),
						event.recipientUserId(),
						batch.size(),
						exception.toString());
				continue;
			}
			collect(event, batch, tickets, unusableTokens, ticketToToken);
		}

		if (!unusableTokens.isEmpty()) {
			int removed = pushTokenService.removeUnusable(unusableTokens);
			log.info("사용할 수 없는 푸시 토큰 삭제 recipientUserId={} removed={}", event.recipientUserId(), removed);
		}
		if (!ticketToToken.isEmpty()) {
			receiptScheduler.schedule(ticketToToken);
		}
	}

	private void collect(
			NotificationSavedEvent event,
			List<PushMessage> batch,
			List<PushTicket> tickets,
			Set<String> unusableTokens,
			Map<String, String> ticketToToken) {
		if (tickets.size() != batch.size()) {
			log.warn(
					"푸시 접수 결과 개수가 다름 type={} sent={} received={}",
					event.type(),
					batch.size(),
					tickets.size());
		}
		for (int i = 0; i < Math.min(batch.size(), tickets.size()); i++) {
			PushTicket ticket = tickets.get(i);
			String token = batch.get(i).token();
			if (ticket.isAccepted()) {
				ticketToToken.put(ticket.id(), token);
			} else if (PushGateway.DEVICE_NOT_REGISTERED.equals(ticket.error())) {
				unusableTokens.add(token);
			} else {
				log.warn(
						"푸시 접수 거절 type={} recipientUserId={} error={}",
						event.type(),
						event.recipientUserId(),
						ticket.error());
			}
		}
	}

	private static List<List<PushMessage>> partition(List<PushMessage> messages) {
		List<List<PushMessage>> batches = new ArrayList<>();
		int size = PushGateway.MAX_MESSAGES_PER_SEND;
		for (int from = 0; from < messages.size(); from += size) {
			batches.add(messages.subList(from, Math.min(from + size, messages.size())));
		}
		return batches;
	}
}
