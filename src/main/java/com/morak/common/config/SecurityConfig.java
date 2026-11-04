package com.morak.common.config;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.ObjectMapper;
import com.morak.common.response.ApiResponse;
import com.morak.feature.auth.domain.exception.AuthErrorCode;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
		return http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/auth/login/**").permitAll()
						.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
						// 웹소켓 핸드셰이크에는 JWT가 없다. 인증은 STOMP CONNECT의 1회용 티켓으로 한다.
						.requestMatchers("/ws").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(errors -> errors
						.authenticationEntryPoint((request, response, exception) -> {
							response.setStatus(AuthErrorCode.AUTHENTICATION_REQUIRED.status().value());
							response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
							response.setContentType(MediaType.APPLICATION_JSON_VALUE);
							objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(
									AuthErrorCode.AUTHENTICATION_REQUIRED.code(),
									AuthErrorCode.AUTHENTICATION_REQUIRED.message()));
						})
						.accessDeniedHandler((request, response, exception) -> {
							response.setStatus(AuthErrorCode.ACCESS_DENIED.status().value());
							response.setContentType(MediaType.APPLICATION_JSON_VALUE);
							objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(
									AuthErrorCode.ACCESS_DENIED.code(), AuthErrorCode.ACCESS_DENIED.message()));
						}))
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
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
