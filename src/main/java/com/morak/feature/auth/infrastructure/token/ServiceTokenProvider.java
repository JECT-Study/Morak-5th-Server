package com.morak.feature.auth.infrastructure.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.morak.feature.auth.application.port.out.ServiceTokenIssuer;
import org.springframework.stereotype.Component;

@Component
public class ServiceTokenProvider implements ServiceTokenIssuer {

	private final JwtEncoder jwtEncoder;
	private final long accessValiditySeconds;
	private final SecureRandom secureRandom = new SecureRandom();

	public ServiceTokenProvider(
			@Value("${morak.auth.jwt.secret}") String secret,
			@Value("${morak.auth.jwt.access-token-validity}") java.time.Duration accessValidity) {
		if (secret.length() < 32) {
			throw new IllegalStateException("JWT_SECRET must contain at least 32 characters");
		}
		SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
		this.accessValiditySeconds = accessValidity.toSeconds();
	}

	@Override
	public String createAccessToken(Long userId, boolean onboarded) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(userId.toString())
				.issuedAt(now)
				.expiresAt(now.plusSeconds(accessValiditySeconds))
				.claim("onboarded", onboarded)
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	@Override
	public String createRefreshToken() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	@Override
	public String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		} catch (java.security.NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	@Override
	public long accessValiditySeconds() { return accessValiditySeconds; }
}
