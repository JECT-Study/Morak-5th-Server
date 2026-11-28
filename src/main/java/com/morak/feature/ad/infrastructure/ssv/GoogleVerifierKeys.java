package com.morak.feature.ad.infrastructure.ssv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.morak.common.exception.BusinessException;
import com.morak.feature.ad.domain.exception.AdErrorCode;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 구글이 공개하는 SSV 서명 키 목록을 가져와 keyId별로 기억한다.
 * 모르는 keyId가 오면 목록을 다시 가져오되, 가짜 keyId로 구글을 계속 호출하지 않게 최소 간격을 둔다.
 */
@Component
class GoogleVerifierKeys implements VerifierKeys {

	private static final Logger log = LoggerFactory.getLogger(GoogleVerifierKeys.class);
	private static final Duration MIN_REFRESH_INTERVAL = Duration.ofMinutes(1);

	private final RestClient client;
	private final String url;
	private final Clock clock;
	private final Map<String, PublicKey> keys = new ConcurrentHashMap<>();
	private Instant lastFetchedAt = Instant.MIN;
	private boolean lastFetchFailed;

	GoogleVerifierKeys(
			@Value("${morak.ad.ssv.verifier-keys-url:https://www.gstatic.com/admob/reward/verifier-keys.json}") String url,
			Clock clock) {
		this.url = url;
		this.clock = clock;
		var requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
		requestFactory.setReadTimeout(Duration.ofSeconds(5));
		this.client = RestClient.builder().requestFactory(requestFactory).build();
	}

	@Override
	public Optional<PublicKey> find(String keyId) {
		PublicKey known = keys.get(keyId);
		if (known != null) {
			return Optional.of(known);
		}
		refreshIfAllowed();
		return Optional.ofNullable(keys.get(keyId));
	}

	private synchronized void refreshIfAllowed() {
		Instant now = clock.instant();
		if (now.isBefore(lastFetchedAt.plus(MIN_REFRESH_INTERVAL))) {
			if (lastFetchFailed) {
				// 방금 실패했다. 모르는 키라고 답하면 구글이 재시도를 멈출 수 있으니 일시 장애로 답한다.
				throw new BusinessException(AdErrorCode.UNAVAILABLE, "구글 SSV 키 목록 조회가 최근에 실패함 url=" + url);
			}
			return;
		}
		try {
			KeysResponse response = client.get().uri(url).retrieve().body(KeysResponse.class);
			if (response == null || response.keys() == null) {
				throw new IllegalStateException("구글 SSV 키 응답에 keys가 없습니다.");
			}
			for (KeyEntry entry : response.keys()) {
				keys.put(entry.keyId(), parse(entry.base64()));
			}
			lastFetchedAt = now;
			lastFetchFailed = false;
		} catch (RestClientException | IllegalStateException | GeneralSecurityException | IllegalArgumentException exception) {
			// 실패한 시도도 간격 제한에 넣으면 구글 장애 때 요청마다 호출하지 않는다. 그동안 모르는 키는 503이다.
			lastFetchedAt = now;
			lastFetchFailed = true;
			log.warn("구글 SSV 키 목록을 가져오지 못했습니다. url={}", url, exception);
			throw new BusinessException(AdErrorCode.UNAVAILABLE, "구글 SSV 키 목록 조회 실패 url=" + url, exception);
		}
	}

	private static PublicKey parse(String base64) throws GeneralSecurityException {
		byte[] der = Base64.getMimeDecoder().decode(base64);
		return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der));
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record KeysResponse(List<KeyEntry> keys) {}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record KeyEntry(String keyId, String base64) {}
}
