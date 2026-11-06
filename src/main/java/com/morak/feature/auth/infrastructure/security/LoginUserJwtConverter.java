package com.morak.feature.auth.infrastructure.security;

import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.auth.application.exception.InvalidLoginUserException;
import com.morak.feature.auth.application.service.AuthenticateUserService;
import com.morak.feature.auth.domain.vo.AuthUserId;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class LoginUserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {
	private final AuthenticateUserService authenticateUserService;

	public LoginUserJwtConverter(AuthenticateUserService authenticateUserService) {
		this.authenticateUserService = authenticateUserService;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		try {
			AuthUserId userId = new AuthUserId(Long.parseLong(jwt.getSubject()));
			LoginUser user = authenticateUserService.authenticate(userId);
			return UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());
		} catch (IllegalArgumentException | InvalidLoginUserException exception) {
			throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), exception);
		}
	}
}
