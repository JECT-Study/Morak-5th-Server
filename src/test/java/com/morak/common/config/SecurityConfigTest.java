package com.morak.common.config;

import com.morak.feature.user.domain.vo.UserId;
import com.morak.feature.auth.application.service.AuthenticateUserService;
import com.morak.feature.auth.infrastructure.security.LoginUserJwtConverter;
import com.morak.feature.auth.infrastructure.token.ServiceTokenProvider;
import com.morak.feature.user.application.service.UserService;
import com.morak.feature.user.domain.entity.User;
import com.morak.feature.user.presentation.controller.UserController;
import com.morak.feature.user.domain.repository.UserRepository;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserController.class,
		properties = "morak.auth.jwt.secret=01234567890123456789012345678901")
@Import({UserService.class, AuthenticateUserService.class, SecurityConfig.class, LoginUserJwtConverter.class})
@ExtendWith(OutputCaptureExtension.class)
class SecurityConfigTest {

	private static final String SECRET = "01234567890123456789012345678901";

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMappingContext;

	@Test
	void missingTokenUsesCommonResponse() throws Exception {
		mvc.perform(get("/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer"))
				.andExpect(jsonPath("$.error.code").value("AUTH_004"));
		verifyNoInteractions(userRepository);
	}

	@ParameterizedTest
	@ValueSource(strings = {"malformed", "wrong-signature", "expired"})
	void invalidTokensUseCommonResponse(String kind) throws Exception {
		String token = switch (kind) {
			case "wrong-signature" -> token("abcdefghijklmnopqrstuvwxyz012345", Duration.ofMinutes(30));
			case "expired" -> token(SECRET, Duration.ofSeconds(1));
			default -> "invalid.jwt.value";
		};
		if (kind.equals("expired")) {
			// Move the decoder's clock beyond the expiry and default clock skew without waiting.
			org.springframework.security.oauth2.jwt.NimbusJwtDecoder decoder =
					(org.springframework.security.oauth2.jwt.NimbusJwtDecoder) jwtDecoder;
			var validator = new org.springframework.security.oauth2.jwt.JwtTimestampValidator(Duration.ZERO);
			validator.setClock(java.time.Clock.offset(java.time.Clock.systemUTC(), Duration.ofMinutes(2)));
			decoder.setJwtValidator(validator);
			try {
				assertUnauthorized(token);
			} finally {
				decoder.setJwtValidator(org.springframework.security.oauth2.jwt.JwtValidators.createDefault());
			}
		} else {
			assertUnauthorized(token);
		}
		verifyNoInteractions(userRepository);
	}

	@Autowired
	private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

	@Test
	void missingUserCannotAuthenticate() throws Exception {
		when(userRepository.findById(new UserId(7L))).thenReturn(Optional.empty());
		assertUnauthorized(token(SECRET, Duration.ofMinutes(30)));
	}

	@Test
	void withdrawnUserCannotAuthenticate() throws Exception {
		User user = new User(null, null);
		ReflectionTestUtils.setField(user, "id", 7L);
		ReflectionTestUtils.setField(user, "deletedAt", LocalDateTime.now());
		when(userRepository.findById(new UserId(7L))).thenReturn(Optional.of(user));
		assertUnauthorized(token(SECRET, Duration.ofMinutes(30)));
	}

	@Test
	void myInfoUsesDatabaseOnboardingStateInsteadOfStaleClaim() throws Exception {
		User user = new User(null, null);
		ReflectionTestUtils.setField(user, "id", 7L);
		ReflectionTestUtils.setField(user, "onboardedAt", LocalDateTime.now());
		when(userRepository.findById(new UserId(7L))).thenReturn(Optional.of(user));
		mvc.perform(get("/users/me").header("Authorization", "Bearer " + token(SECRET, Duration.ofMinutes(30))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(7))
				.andExpect(jsonPath("$.data.onboarded").value(true));
	}

	private void assertUnauthorized(String token) throws Exception {
		mvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("AUTH_004"));
	}

	private String token(String secret, Duration validity) {
		return new ServiceTokenProvider(secret, validity).createAccessToken(new UserId(7L), false).value();
	}

	@Test
	void memberCanReadOwnInfoBeforeOnboarding() throws Exception {
		User user = new User("member@example.com", "https://example.com/profile.png");
		ReflectionTestUtils.setField(user, "id", 7L);
		ReflectionTestUtils.setField(user, "termsAgreedAt", LocalDateTime.now());
		when(userRepository.findById(new UserId(7L))).thenReturn(Optional.of(user));

		mvc.perform(get("/users/me").param("userId", "8")
						.header("Authorization", "Bearer " + token(SECRET, Duration.ofMinutes(30))))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.userId").value(7))
				.andExpect(jsonPath("$.data.email").value("member@example.com"))
				.andExpect(jsonPath("$.data.nickname").isEmpty())
				.andExpect(jsonPath("$.data.profileImageUrl").value("https://example.com/profile.png"))
				.andExpect(jsonPath("$.data.termsAgreed").value(true))
				.andExpect(jsonPath("$.data.marketingAgreed").value(false))
				.andExpect(jsonPath("$.data.onboarded").value(false));
		verify(userRepository, never()).findById(new UserId(8L));
	}
	@Test
	void encodedMyInfoPathDoesNotLogProfile(CapturedOutput output) throws Exception {
		String email = "encoded-profile@example.com";
		User user = new User(email, null);
		ReflectionTestUtils.setField(user, "id", 7L);
		when(userRepository.findById(new UserId(7L))).thenReturn(Optional.of(user));

		mvc.perform(get(URI.create("/users/%6De"))
						.header("Authorization", "Bearer " + token(SECRET, Duration.ofMinutes(30))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value(email));

		assertTrue(output.getOut().contains("response=<omitted: member profile>"));
		assertFalse(output.getAll().contains(email));
	}

}
