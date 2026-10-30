package com.morak.feature.auth.infrastructure.oauth;

import tools.jackson.databind.JsonNode;
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

	private final RestClient restClient = RestClient.create();
	private final String clientId;
	private final String clientSecret;
	private final String redirectUri;

	public KakaoOAuthClient(
			@Value("${morak.auth.kakao.client-id:}") String clientId,
			@Value("${morak.auth.kakao.client-secret:}") String clientSecret,
			@Value("${morak.auth.kakao.redirect-uri}") String redirectUri) {
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
			throw new OAuthProviderException(exception.getStatusCode().is4xxClientError(), exception);
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
		if (profile == null || profile.path("id").isMissingNode()) {
			throw new OAuthProviderException(false, new IllegalStateException("Kakao did not return a user ID"));
		}

		JsonNode account = profile.path("kakao_account");
		JsonNode properties = profile.path("properties");
		return new SocialProfile(
				provider(),
				profile.path("id").asText(),
				account.path("email").asText(null),
				properties.path("profile_image").asText(null));
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
