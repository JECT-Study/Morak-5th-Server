package com.morak.common.config;

import com.morak.feature.user.domain.vo.Nickname;
import com.morak.feature.auth.domain.vo.AuthUserId;
import com.morak.feature.user.domain.vo.UserId;
import com.morak.feature.auth.application.service.AuthenticateUserService;
import com.morak.feature.auth.infrastructure.security.LoginUserJwtConverter;
import com.morak.feature.auth.infrastructure.user.UserAuthAdapter;
import com.morak.feature.auth.infrastructure.token.ServiceTokenProvider;
import com.morak.feature.user.application.service.UserService;
import com.morak.feature.user.domain.entity.User;
import com.morak.feature.user.presentation.controller.UserController;
import com.morak.feature.user.domain.repository.UserRepository;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserController.class,
		properties = "morak.auth.jwt.secret=01234567890123456789012345678901")
@Import({UserService.class, UserAuthAdapter.class, AuthenticateUserService.class, SecurityConfig.class, LoginUserJwtConverter.class,
		SecurityConfigTest.OnboardedOnlyController.class})
@ExtendWith(OutputCaptureExtension.class)
class SecurityConfigTest {

	private static final String SECRET = "01234567890123456789012345678901";

	@RestController
	static class OnboardedOnlyController {
		@GetMapping("/test/onboarded-only")
		String get() { return "ok"; }
	}

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

	@Test
	void requiresOnboardingForOtherRoutesUsingCurrentDatabaseState() throws Exception {
		User user = new User(null, null);
		ReflectionTestUtils.setField(user, "id", 7L);
		when(userRepository.findById(new UserId(7L))).thenReturn(Optional.of(user));
		String bearer = "Bearer " + token(SECRET, Duration.ofMinutes(30));

		mvc.perform(get("/test/onboarded-only")).andExpect(status().isUnauthorized());
		mvc.perform(get("/test/onboarded-only").header("Authorization", bearer))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("AUTH_005"));

		ReflectionTestUtils.setField(user, "onboardedAt", LocalDateTime.now());
		mvc.perform(get("/test/onboarded-only").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(content().string("ok"));
	}

	private void assertUnauthorized(String token) throws Exception {
		mvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("AUTH_004"));
	}

	private String token(String secret, Duration validity) {
		return new ServiceTokenProvider(secret, validity).createAccessToken(new AuthUserId(7L), false).value();
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


	@Test
	void nicknameAvailabilityUsesExactCase() throws Exception {
		User user = new User(null, null);
		ReflectionTestUtils.setField(user, "id", 7L);
		when(userRepository.findById(new UserId(7L))).thenReturn(Optional.of(user));
		when(userRepository.existsByNickname(new Nickname("Morak"))).thenReturn(true);
		String bearer = "Bearer " + token(SECRET, Duration.ofMinutes(30));

		mvc.perform(get("/users/nickname-check").param("nickname", "Morak").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.available").value(false));
		mvc.perform(get("/users/nickname-check").param("nickname", "morak").header("Authorization", bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.available").value(true));
		verify(userRepository).existsByNickname(new Nickname("morak"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"A", "abcdefghijklm", "Mo rak", "Morak!"})
	void invalidNicknameDoesNotQueryAvailability(String nickname) throws Exception {
		User user = new User(null, null);
		ReflectionTestUtils.setField(user, "id", 7L);
		when(userRepository.findById(new UserId(7L))).thenReturn(Optional.of(user));

		mvc.perform(get("/users/nickname-check").param("nickname", nickname)
						.header("Authorization", "Bearer " + token(SECRET, Duration.ofMinutes(30))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_001"));
		verify(userRepository, never()).existsByNickname(any());
	}

	@Test
	void recordsRequiredAgreementsAndAllowsOnlyMarketingToChangeAfterwards() throws Exception {
		User user = userForTermsAgreements();
		String bearer = "Bearer " + token(SECRET, Duration.ofMinutes(30));
		String initialAgreements = """
				{"age14OrOlder":true,"serviceTermsAgreed":true,"privacyAgreed":true,
				 "photoCollectionAgreed":true,"marketingAgreed":false}
				""";
		mvc.perform(post("/users/me/terms-agreements").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON).content(initialAgreements))
				.andExpect(status().isOk());
		assertTrue(user.hasAgreedToTerms());
		assertFalse(user.isMarketingAgreed());
		Object agreedAt = ReflectionTestUtils.getField(user, "termsAgreedAt");

		mvc.perform(patch("/users/me/marketing-agreements").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON).content("{\"marketingAgreed\":true}"))
				.andExpect(status().isOk());
		assertTrue(user.isMarketingAgreed());
		assertEquals(agreedAt, ReflectionTestUtils.getField(user, "termsAgreedAt"));

		mvc.perform(post("/users/me/terms-agreements").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON).content(initialAgreements))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("USER_001"));
		assertTrue(user.isMarketingAgreed());
		assertEquals(agreedAt, ReflectionTestUtils.getField(user, "termsAgreedAt"));
	}

	@Test
	void rejectsMarketingOnlyBeforeRequiredAgreementsAreAccepted() throws Exception {
		User user = userForTermsAgreements();
		mvc.perform(post("/users/me/terms-agreements")
						.header("Authorization", "Bearer " + token(SECRET, Duration.ofMinutes(30)))
						.contentType(MediaType.APPLICATION_JSON).content("{\"marketingAgreed\":true}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_001"));
		assertFalse(user.hasAgreedToTerms());
		assertFalse(user.isMarketingAgreed());
	}

	@Test
	void rejectsMarketingChangesBeforeRequiredAgreementsAreAccepted() throws Exception {
		User user = userForTermsAgreements();
		mvc.perform(patch("/users/me/marketing-agreements")
						.header("Authorization", "Bearer " + token(SECRET, Duration.ofMinutes(30)))
						.contentType(MediaType.APPLICATION_JSON).content("{\"marketingAgreed\":true}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("USER_002"));
		assertFalse(user.isMarketingAgreed());
	}

	@Test
	void completesOnboardingOnceAndSavesSelectedInterests() throws Exception {
		User user = userForTermsAgreements();
		ReflectionTestUtils.setField(user, "termsAgreedAt", LocalDateTime.now());
		String bearer = "Bearer " + token(SECRET, Duration.ofMinutes(30));
		String profile = """
				{"nickname":"Morak","bio":"안녕하세요","profileLinkUrl":"https://example.com",
				 "interests":["STUDY","EXERCISE"]}
				""";

		mvc.perform(post("/users/me/onboarding").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON).content(profile))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.nickname").value("Morak"))
				.andExpect(jsonPath("$.data.onboarded").value(true));
		assertEquals("안녕하세요", user.getBio());
		assertEquals("https://example.com", user.getProfileLinkUrl());
		assertEquals(Set.of("STUDY", "EXERCISE"),
				ReflectionTestUtils.getField(user, "interests"));

		mvc.perform(post("/users/me/onboarding").header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON).content(profile))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("USER_003"));
		verify(userRepository).flushOnboarding();
	}

	@Test
	void requiresTermsBeforeCompletingOnboarding() throws Exception {
		User user = userForTermsAgreements();
		mvc.perform(post("/users/me/onboarding")
						.header("Authorization", "Bearer " + token(SECRET, Duration.ofMinutes(30)))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nickname\":\"Morak\",\"interests\":[\"EXERCISE\"]}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("USER_002"));
		assertFalse(user.isOnboarded());
		verify(userRepository, never()).flushOnboarding();
	}

	@ParameterizedTest
	@ValueSource(strings = {"[]", "[\"UNKNOWN\"]", "[\"EXERCISE_HEALTH\"]", "[null]"})
	void rejectsInvalidInterestSelections(String interests) throws Exception {
		User user = new User(null, null);
		ReflectionTestUtils.setField(user, "id", 7L);
		when(userRepository.findById(new UserId(7L))).thenReturn(Optional.of(user));
		mvc.perform(post("/users/me/onboarding")
						.header("Authorization", "Bearer " + token(SECRET, Duration.ofMinutes(30)))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nickname\":\"Morak\",\"interests\":" + interests + "}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_001"));
		verify(userRepository, never()).findByIdForUpdate(any());
	}

	private User userForTermsAgreements() {
		User user = new User(null, null);
		ReflectionTestUtils.setField(user, "id", 7L);
		UserId userId = new UserId(7L);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
		return user;
	}
}
