package com.morak.feature.auth.infrastructure.oauth;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import com.morak.feature.auth.application.port.out.OAuthProviderException;
import com.morak.feature.auth.application.port.out.SocialOAuthProvider;
import com.morak.feature.auth.application.port.out.SocialProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class KakaoOAuthClient implements SocialOAuthProvider {

	private final RestClient restClient;
	private final String clientId;
	private final String clientSecret;
	private final String redirectUri;

	public KakaoOAuthClient(
			@Value("${morak.auth.kakao.client-id:}") String clientId,
			@Value("${morak.auth.kakao.client-secret:}") String clientSecret,
			@Value("${morak.auth.kakao.redirect-uri}") String redirectUri) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(3));
		requestFactory.setReadTimeout(Duration.ofSeconds(5));
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.redirectUri = redirectUri;
	}

	@Override
	public String provider() {
		return "KAKAO";
	}

	@Override
	public SocialProfile getProfile(String authorizationCode) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", clientId);
		form.add("redirect_uri", redirectUri);
		form.add("code", authorizationCode);
		if (!clientSecret.isBlank()) {
			form.add("client_secret", clientSecret);
		}

		JsonNode token;
		try {
			token = restClient.post()
					.uri("https://kauth.kakao.com/oauth/token")
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(form)
					.retrieve()
					.body(JsonNode.class);
		} catch (RestClientResponseException exception) {
			throw new OAuthProviderException(isInvalidAuthorization(exception), exception);
		} catch (RuntimeException exception) {
			throw new OAuthProviderException(false, exception);
		}
		if (token == null || token.path("access_token").isMissingNode()) {
			throw new OAuthProviderException(false, new IllegalStateException("Kakao did not return an access token"));
		}

		JsonNode profile;
		try {
			profile = restClient.get()
					.uri("https://kapi.kakao.com/v2/user/me")
					.header("Authorization", "Bearer " + token.path("access_token").asText())
					.retrieve()
					.body(JsonNode.class);
		} catch (RuntimeException exception) {
			throw new OAuthProviderException(false, exception);
		}
		if (profile == null || !profile.path("id").isIntegralNumber()) {
			throw new OAuthProviderException(false, new IllegalStateException("Kakao did not return a user ID"));
		}

		JsonNode account = profile.path("kakao_account");
		JsonNode properties = profile.path("properties");
		String profileImageUrl = account.path("profile").path("profile_image_url").asText(null);
		if (profileImageUrl == null || profileImageUrl.isBlank()) {
			profileImageUrl = properties.path("profile_image").asText(null);
		}
		return new SocialProfile(
				provider(),
				profile.path("id").asText(),
				account.path("email").asText(null),
				profileImageUrl);
	}

	private boolean isInvalidAuthorization(RestClientResponseException exception) {
		if (!exception.getStatusCode().is4xxClientError()) {
			return false;
		}
		try {
			JsonNode error = new ObjectMapper().readTree(exception.getResponseBodyAsString());
			return error != null && "invalid_grant".equals(error.path("error").asText());
		} catch (RuntimeException parsingException) {
			return false;
		}
	}

	@Override
	public String authorizationUri(String state) {
		return UriComponentsBuilder.fromUriString("https://kauth.kakao.com/oauth/authorize")
				.queryParam("response_type", "code")
				.queryParam("client_id", clientId)
				.queryParam("redirect_uri", redirectUri)
				.queryParam("state", state)
				.build()
				.encode()
				.toUriString();
	}
}
