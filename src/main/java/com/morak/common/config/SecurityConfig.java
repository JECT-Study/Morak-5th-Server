package com.morak.common.config;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.ObjectMapper;
import com.morak.common.response.ApiResponse;
import com.morak.feature.auth.application.dto.result.LoginUser;
import com.morak.feature.auth.domain.exception.AuthErrorCode;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.morak.feature.auth.infrastructure.security.LoginUserJwtConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
			LoginUserJwtConverter loginUserJwtConverter) throws Exception {
		AuthenticationEntryPoint entryPoint = (request, response, exception) -> {
			response.setStatus(AuthErrorCode.AUTHENTICATION_REQUIRED.status().value());
			response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(
					AuthErrorCode.AUTHENTICATION_REQUIRED.code(), AuthErrorCode.AUTHENTICATION_REQUIRED.message()));
		};
		AccessDeniedHandler deniedHandler = (request, response, exception) -> {
			response.setStatus(AuthErrorCode.ACCESS_DENIED.status().value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(
					AuthErrorCode.ACCESS_DENIED.code(), AuthErrorCode.ACCESS_DENIED.message()));
		};
		return http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/auth/login/**").permitAll()
						.requestMatchers(HttpMethod.POST, "/auth/refresh", "/auth/logout").permitAll()
						.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
						// 웹소켓 핸드셰이크에는 JWT가 없다. 인증은 STOMP CONNECT의 1회용 티켓으로 한다.
						.requestMatchers("/ws").permitAll()
						// 위에서부터 처음 일치하는 규칙을 적용한다. 아래 API는 온보딩 전에도 로그인하면 호출할 수 있다.
						.requestMatchers(HttpMethod.GET, "/users/me", "/users/nickname-check").authenticated()
						.requestMatchers(HttpMethod.HEAD, "/users/me", "/users/nickname-check").authenticated()
						.requestMatchers(HttpMethod.POST, "/users/me/terms-agreements", "/users/me/onboarding")
								.authenticated()
						.requestMatchers(HttpMethod.PATCH, "/users/me/marketing-agreements").authenticated()
						// 나머지 API는 온보딩 완료가 필요하다. LoginUser에는 요청 시 DB에서 조회한 상태가 들어 있다.
						// 미완료 회원은 위의 deniedHandler에서 403을 받고, 미인증 요청은 entryPoint에서 401을 받는다.
						.anyRequest().access((authentication, context) -> new AuthorizationDecision(
								authentication.get().getPrincipal() instanceof LoginUser user && user.onboarded())))
				.exceptionHandling(errors -> errors
						.authenticationEntryPoint(entryPoint)
						.accessDeniedHandler(deniedHandler))
				.oauth2ResourceServer(oauth2 -> oauth2
						.authenticationEntryPoint(entryPoint)
						.accessDeniedHandler(deniedHandler)
						.jwt(jwt -> jwt.jwtAuthenticationConverter(loginUserJwtConverter)))
				.build();
	}

	@Bean
	JwtDecoder jwtDecoder(@Value("${morak.auth.jwt.secret}") String secret) {
		if (secret.length() < 32) {
			throw new IllegalStateException("JWT_SECRET must contain at least 32 characters");
		}
		SecretKey key = new SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
		return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
	}
}
