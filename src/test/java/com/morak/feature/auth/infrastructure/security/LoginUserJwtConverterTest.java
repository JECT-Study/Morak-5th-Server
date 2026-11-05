package com.morak.feature.auth.infrastructure.security;

import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.auth.application.service.AuthenticateUserService;
import com.morak.feature.auth.infrastructure.user.UserAuthAdapter;
import com.morak.feature.user.application.service.UserService;

import com.morak.feature.user.domain.entity.User;
import com.morak.feature.user.domain.repository.UserRepository;
import com.morak.feature.auth.domain.vo.AuthUserId;
import com.morak.feature.user.domain.vo.UserId;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginUserJwtConverterTest {

	private final UserRepository repository = mock(UserRepository.class);
	private final LoginUserJwtConverter converter =
			new LoginUserJwtConverter(new AuthenticateUserService(new UserAuthAdapter(new UserService(repository))));

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"", "abc", "0", "-1", "9223372036854775808"})
	void rejectsInvalidSubjectBeforeQueryingDatabase(String subject) {
		assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(jwt(subject)));
		verifyNoInteractions(repository);
	}

	@Test
	void authenticatesUserBeforeOnboarding() {
		User user = new User(null, null);
		ReflectionTestUtils.setField(user, "id", 7L);
		when(repository.findById(new UserId(7L))).thenReturn(Optional.of(user));
		var authentication = converter.convert(jwt("7"));
		assertTrue(authentication.isAuthenticated());
		assertEquals(new LoginUser(new AuthUserId(7L), false), authentication.getPrincipal());
		assertEquals("7", authentication.getName());
		assertNull(authentication.getCredentials());
	}

	@Test
	void rejectsInactiveUser() {
		User user = new User(null, null);
		ReflectionTestUtils.setField(user, "id", 7L);
		ReflectionTestUtils.setField(user, "status", "INACTIVE");
		when(repository.findById(new UserId(7L))).thenReturn(Optional.of(user));
		assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(jwt("7")));
	}

	private Jwt jwt(String subject) {
		var builder = Jwt.withTokenValue("token").header("alg", "HS256").issuedAt(Instant.now());
		if (subject != null) {
			builder.subject(subject);
		}
		return builder.build();
	}
}
