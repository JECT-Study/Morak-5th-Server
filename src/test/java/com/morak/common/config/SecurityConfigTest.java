package com.morak.common.config;

import com.morak.feature.user.domain.vo.UserId;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.auth.infrastructure.security.LoginUserJwtConverter;
import com.morak.feature.auth.infrastructure.token.ServiceTokenProvider;
import com.morak.feature.user.domain.entity.User;
import com.morak.feature.user.domain.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SecurityConfigTest.ProbeController.class,
		properties = "morak.auth.jwt.secret=01234567890123456789012345678901")
@Import({com.morak.feature.user.application.service.UserService.class,com.morak.feature.auth.application.service.AuthenticateUserService.class, SecurityConfig.class, LoginUserJwtConverter.class, SecurityConfigTest.ProbeController.class})
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
		mvc.perform(get("/security-probe"))
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
	void principalUsesDatabaseOnboardingStateInsteadOfStaleClaim() throws Exception {
		User user = new User(null, null);
		ReflectionTestUtils.setField(user, "id", 7L);
		ReflectionTestUtils.setField(user, "onboardedAt", LocalDateTime.now());
		when(userRepository.findById(new UserId(7L))).thenReturn(Optional.of(user));
		mvc.perform(get("/security-probe").header("Authorization", "Bearer " + token(SECRET, Duration.ofMinutes(30))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId.value").value(7))
				.andExpect(jsonPath("$.onboarded").value(true));
	}

	private void assertUnauthorized(String token) throws Exception {
		mvc.perform(get("/security-probe").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("AUTH_004"));
	}

	private String token(String secret, Duration validity) {
		return new ServiceTokenProvider(secret, validity).createAccessToken(new UserId(7L), false).value();
	}

	@RestController
	static class ProbeController {
		@GetMapping("/security-probe")
		LoginUser probe(@AuthenticationPrincipal LoginUser user) {
			return user;
		}
	}
}
