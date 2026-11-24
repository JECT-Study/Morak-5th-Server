package com.morak.feature.auth.infrastructure.oauth;

import com.morak.feature.auth.domain.vo.SocialProvider;
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
public class GoogleOAuthClient implements SocialOAuthProvider {

	private final RestClient restClient;
	private final String clientId;
	private final String clientSecret;
	private final String redirectUri;

	public GoogleOAuthClient(
			@Value("${morak.auth.google.client-id:}") String clientId,
			@Value("${morak.auth.google.client-secret:}") String clientSecret,
			@Value("${morak.auth.google.redirect-uri}") String redirectUri) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(3));
		requestFactory.setReadTimeout(Duration.ofSeconds(5));
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.redirectUri = redirectUri;
	}

	@Override
	public SocialProvider provider() {
		return SocialProvider.GOOGLE;
	}

	@Override
	public SocialProfile getProfile(String authorizationCode) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", clientId);
		form.add("client_secret", clientSecret);
		form.add("redirect_uri", redirectUri);
		form.add("code", authorizationCode);

		JsonNode token;
		try {
			token = restClient.post()
					.uri("https://oauth2.googleapis.com/token")
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
			throw new OAuthProviderException(false, new IllegalStateException("Google did not return an access token"));
		}

		JsonNode profile;
		try {
			profile = restClient.get()
					.uri("https://openidconnect.googleapis.com/v1/userinfo")
					.header("Authorization", "Bearer " + token.path("access_token").asText())
					.retrieve()
					.body(JsonNode.class);
		} catch (RuntimeException exception) {
			throw new OAuthProviderException(false, exception);
		}
		if (profile == null || !profile.path("sub").isString() || profile.path("sub").asText().isBlank()) {
			throw new OAuthProviderException(false, new IllegalStateException("Google did not return a user ID"));
		}

		// 인증되지 않은 이메일은 저장하지 않는다.
		String email = profile.path("email_verified").asBoolean(false) ? profile.path("email").asText(null) : null;
		String picture = profile.path("picture").asText(null);
		return new SocialProfile(
				provider().name(),
				profile.path("sub").asText(),
				email,
				picture == null || picture.isBlank() ? null : picture);
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
		return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
				.queryParam("response_type", "code")
				.queryParam("client_id", clientId)
				.queryParam("redirect_uri", redirectUri)
				.queryParam("scope", "openid email profile")
				.queryParam("state", state)
				.build()
				.encode()
				.toUriString();
	}
}
