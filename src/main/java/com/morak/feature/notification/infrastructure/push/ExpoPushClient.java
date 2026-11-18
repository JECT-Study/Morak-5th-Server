package com.morak.feature.notification.infrastructure.push;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.morak.feature.notification.application.dto.PushMessage;
import com.morak.feature.notification.application.dto.PushReceipt;
import com.morak.feature.notification.application.dto.PushTicket;
import com.morak.feature.notification.application.port.out.PushGateway;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Expo Push 서비스 호출. 요청·응답 형식은 Expo 규격을 따르고, 우리가 정하는 것은 data 안의 내용뿐이다.
 * 호출이 실패하면 RestClientException 같은 RuntimeException이 그대로 올라간다.
 * morak.push.expo.enabled=true일 때만 만들어진다.
 */
@Component
@ConditionalOnProperty(name = "morak.push.expo.enabled", havingValue = "true")
class ExpoPushClient implements PushGateway {

	private static final String STATUS_OK = "ok";
	private static final String UNKNOWN_ERROR = "Unknown";

	private final RestClient client;
	private final String sendUrl;
	private final String receiptsUrl;
	private final String accessToken;

	ExpoPushClient(
			@Value("${morak.push.expo.send-url}") String sendUrl,
			@Value("${morak.push.expo.receipts-url}") String receiptsUrl,
			@Value("${morak.push.expo.access-token:}") String accessToken) {
		this.sendUrl = sendUrl;
		this.receiptsUrl = receiptsUrl;
		this.accessToken = accessToken == null ? "" : accessToken.trim();
		var requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
		requestFactory.setReadTimeout(Duration.ofSeconds(10));
		this.client = RestClient.builder().requestFactory(requestFactory).build();
	}

	@Override
	public List<PushTicket> send(List<PushMessage> messages) {
		List<ExpoMessage> body = messages.stream().map(ExpoPushClient::toExpoMessage).toList();
		SendResponse response = request(sendUrl).body(body).retrieve().body(SendResponse.class);
		if (response == null || response.data() == null) {
			throw new IllegalStateException("Expo 응답에 data가 없습니다.");
		}
		return response.data().stream().map(ExpoPushClient::toTicket).toList();
	}

	@Override
	public Map<String, PushReceipt> getReceipts(List<String> ticketIds) {
		ReceiptsResponse response = request(receiptsUrl)
				.body(Map.of("ids", ticketIds))
				.retrieve()
				.body(ReceiptsResponse.class);
		if (response == null || response.data() == null) {
			throw new IllegalStateException("Expo 영수증 응답에 data가 없습니다.");
		}
		Map<String, PushReceipt> receipts = new LinkedHashMap<>();
		response.data().forEach((id, receipt) -> receipts.put(id, toReceipt(receipt)));
		return receipts;
	}

	private RestClient.RequestBodySpec request(String url) {
		return client.post()
				.uri(url)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.headers(headers -> {
					if (!accessToken.isEmpty()) {
						headers.setBearerAuth(accessToken);
					}
				});
	}

	private static ExpoMessage toExpoMessage(PushMessage message) {
		Map<String, String> data = message.url() == null ? null : Map.of("url", message.url());
		return new ExpoMessage(message.token(), message.title(), message.body(), "default", data);
	}

	private static PushTicket toTicket(ExpoResult result) {
		if (result == null) {
			return PushTicket.rejected(UNKNOWN_ERROR);
		}
		if (STATUS_OK.equals(result.status()) && result.id() != null) {
			return PushTicket.accepted(result.id());
		}
		return PushTicket.rejected(errorOf(result));
	}

	private static PushReceipt toReceipt(ExpoResult result) {
		if (result != null && STATUS_OK.equals(result.status())) {
			return PushReceipt.ok();
		}
		return PushReceipt.failed(errorOf(result));
	}

	private static String errorOf(ExpoResult result) {
		if (result == null) {
			return UNKNOWN_ERROR;
		}
		if (result.details() != null && result.details().error() != null) {
			return result.details().error();
		}
		return UNKNOWN_ERROR;
	}

	/** Expo가 요구하는 이름 그대로의 요청 필드. data는 없으면 보내지 않는다. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	record ExpoMessage(String to, String title, String body, String sound, Map<String, String> data) {}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SendResponse(List<ExpoResult> data) {}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ReceiptsResponse(Map<String, ExpoResult> data) {}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ExpoResult(String status, String id, String message, ExpoDetails details) {}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ExpoDetails(String error) {}
}
