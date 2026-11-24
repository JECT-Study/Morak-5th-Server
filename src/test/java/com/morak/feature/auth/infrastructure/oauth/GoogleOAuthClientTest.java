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

class GoogleOAuthClientTest {

	private final RestClient.Builder builder = RestClient.builder();
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
	private final GoogleOAuthClient client = new GoogleOAuthClient("client", "secret", "https://example.com/callback");

	GoogleOAuthClientTest() {
		ReflectionTestUtils.setField(client, "restClient", builder.build());
	}

	@Test
	void distinguishesAuthorizationErrorsFromProviderErrors() {
		for (String error : new String[] {"invalid_grant", "invalid_client", "invalid_request"}) {
			server.expect(requestTo("https://oauth2.googleapis.com/token"))
					.andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
							.body("{\"error\":\"" + error + "\"}"));
			OAuthProviderException exception = assertThrows(OAuthProviderException.class, () -> client.getProfile("code"));
			assertEquals("invalid_grant".equals(error), exception.isInvalidAuthorization());
			server.verify();
			server.reset();
		}
	}

	@Test
	void rejectsMissingSubject() {
		for (String profile : new String[] {"{}", "{\"sub\":null}", "{\"sub\":\"\"}", "{\"sub\":123}"}) {
			expectProfile(profile);
			OAuthProviderException exception = assertThrows(OAuthProviderException.class, () -> client.getProfile("code"));
			assertFalse(exception.isInvalidAuthorization());
			server.verify();
			server.reset();
		}
	}

	@Test
	void mapsProfileAndKeepsOnlyVerifiedEmail() {
		expectProfile("{\"sub\":\"1098765\",\"email\":\"a@example.com\",\"email_verified\":true,"
				+ "\"picture\":\"https://example.com/p.png\"}");
		SocialProfile profile = client.getProfile("code");
		assertEquals("GOOGLE", profile.provider());
		assertEquals("1098765", profile.providerUserId());
		assertEquals("a@example.com", profile.email());
		assertEquals("https://example.com/p.png", profile.profileImageUrl());
		server.verify();
		server.reset();

		expectProfile("{\"sub\":\"1098765\",\"email\":\"a@example.com\",\"email_verified\":false}");
		profile = client.getProfile("code");
		assertNull(profile.email());
		assertNull(profile.profileImageUrl());
		server.verify();
	}

	private void expectProfile(String profile) {
		server.expect(requestTo("https://oauth2.googleapis.com/token"))
				.andRespond(withSuccess("{\"access_token\":\"token\"}", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
				.andRespond(withSuccess(profile, MediaType.APPLICATION_JSON));
	}
}
