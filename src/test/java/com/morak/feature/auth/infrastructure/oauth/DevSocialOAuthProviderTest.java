package com.morak.feature.auth.infrastructure.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.feature.auth.application.port.out.OAuthProviderException;
import com.morak.feature.auth.application.port.out.SocialProfile;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

class DevSocialOAuthProviderTest {

	private final DevSocialOAuthProvider provider = new DevSocialOAuthProvider();

	@Test
	void usesCodeAsProviderUserId() {
		SocialProfile profile = provider.getProfile("tester-1");

		assertEquals("DEV", profile.provider());
		assertEquals("tester-1", profile.providerUserId());
		assertNull(profile.email());
		assertNull(profile.profileImageUrl());
	}

	@Test
	void rejectsBlankOrMalformedCode() {
		for (String code : new String[] {null, "", "has space", "한글", "a".repeat(65)}) {
			OAuthProviderException exception = assertThrows(OAuthProviderException.class, () -> provider.getProfile(code));
			assertTrue(exception.isInvalidAuthorization());
		}
	}

	@Test
	void isRegisteredOnlyForLocalProfile() {
		Profile profile = DevSocialOAuthProvider.class.getAnnotation(Profile.class);

		assertEquals(1, profile.value().length);
		assertEquals("local", profile.value()[0]);
	}
}
