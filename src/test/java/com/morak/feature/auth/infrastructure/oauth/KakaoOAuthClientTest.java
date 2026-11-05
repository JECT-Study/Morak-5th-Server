package com.morak.feature.auth.infrastructure.oauth;

import com.morak.feature.auth.application.port.out.SocialProfile;
import com.morak.feature.auth.application.port.out.OAuthProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class KakaoOAuthClientTest {

	private final RestClient.Builder builder = RestClient.builder();
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
	private final KakaoOAuthClient client = new KakaoOAuthClient("client", "secret", "https://example.com/callback");

	KakaoOAuthClientTest() {
		ReflectionTestUtils.setField(client, "restClient", builder.build());
	}

	@Test
	void distinguishesAuthorizationErrorsFromProviderErrors() {
		for (String error : new String[] {"invalid_grant", "invalid_client", "invalid_request"}) {
			server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
					.andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
							.body("{\"error\":\"" + error + "\"}"));
			OAuthProviderException exception = assertThrows(OAuthProviderException.class, () -> client.getProfile("code"));
			assertEquals("invalid_grant".equals(error), exception.isInvalidAuthorization());
			server.verify();
			server.reset();
		}
	}

	@Test
	void rejectsMalformedLongs() {
		for (String profile : new String[] {"{}", "{\"id\":null}", "{\"id\":\"\"}", "{\"id\":\"123\"}", "{\"id\":1.5}"}) {
			expectProfile(profile);
			OAuthProviderException exception = assertThrows(OAuthProviderException.class, () -> client.getProfile("code"));
			assertFalse(exception.isInvalidAuthorization());
			server.verify();
			server.reset();
		}
	}

	@Test
	void prefersCurrentImageAndFallsBackToLegacyImage() {
		for (String image : new String[] {"https://example.com/new.png", "", " "}) {
			expectProfile("{\"id\":12345678901,\"kakao_account\":{\"profile\":{\"profile_image_url\":\""
					+ image + "\"}},\"properties\":{\"profile_image\":\"https://example.com/old.png\"}}");
			SocialProfile profile = client.getProfile("code");
			assertEquals("12345678901", profile.providerUserId());
			assertEquals(image.isBlank() ? "https://example.com/old.png" : image, profile.profileImageUrl());
			server.verify();
			server.reset();
		}
	}

	private void expectProfile(String profile) {
		server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
				.andRespond(withSuccess("{\"access_token\":\"token\"}", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
				.andRespond(withSuccess(profile, MediaType.APPLICATION_JSON));
	}
}
