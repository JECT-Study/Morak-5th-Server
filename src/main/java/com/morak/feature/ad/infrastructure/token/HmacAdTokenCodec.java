package com.morak.feature.ad.infrastructure.token;

import com.morak.common.exception.BusinessException;
import com.morak.feature.ad.application.port.out.AdTokenCodec;
import com.morak.feature.ad.domain.exception.AdErrorCode;
import com.morak.feature.ad.domain.vo.AdToken;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 광고 시청 토큰을 HMAC-SHA256으로 서명한다. 형식: base64url(id|userId|발급초|만료초).base64url(서명).
 * 서버에 저장하지 않고, 1회용은 원장 멱등키(AD:{id})로 보장한다.
 */
@Component
class HmacAdTokenCodec implements AdTokenCodec {

	private static final String ALGORITHM = "HmacSHA256";
	private static final int MAX_TOKEN_LENGTH = 512;
	private static final Pattern TOKEN_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

	private final byte[] secret;

	HmacAdTokenCodec(@Value("${morak.ad.token-secret:}") String secret) {
		this.secret = secret.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public String issue(AdToken token) {
		requireSecret();
		String payload = ENCODER.encodeToString((token.id() + "|" + token.userId() + "|"
				+ token.issuedAt().getEpochSecond() + "|" + token.expiresAt().getEpochSecond())
				.getBytes(StandardCharsets.UTF_8));
		return payload + "." + ENCODER.encodeToString(sign(payload));
	}

	@Override
	public AdToken parse(String raw) {
		requireSecret();
		if (raw == null || raw.length() > MAX_TOKEN_LENGTH) {
			throw invalid("길이 오류 length=" + (raw == null ? "null" : raw.length()));
		}
		String[] parts = raw.split("\\.", -1);
		if (parts.length != 2) {
			throw invalid("형식 오류");
		}
		try {
			byte[] signature = DECODER.decode(parts[1]);
			if (!MessageDigest.isEqual(signature, sign(parts[0]))) {
				throw invalid("서명 불일치");
			}
			String[] fields = new String(DECODER.decode(parts[0]), StandardCharsets.UTF_8).split("\\|", -1);
			if (fields.length != 4 || !TOKEN_ID.matcher(fields[0]).matches()) {
				throw invalid("내용 오류");
			}
			return new AdToken(
					fields[0],
					Long.parseLong(fields[1]),
					Instant.ofEpochSecond(Long.parseLong(fields[2])),
					Instant.ofEpochSecond(Long.parseLong(fields[3])));
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(AdErrorCode.INVALID_TOKEN, "토큰 해석 실패", exception);
		}
	}

	private byte[] sign(String payload) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(secret, ALGORITHM));
			return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException(exception);
		}
	}

	/** 서명 키가 없으면 토큰을 만들지도 믿지도 않는다. */
	private void requireSecret() {
		if (secret.length == 0) {
			throw new BusinessException(AdErrorCode.UNAVAILABLE, "morak.ad.token-secret 미설정");
		}
	}

	private static BusinessException invalid(String detail) {
		return new BusinessException(AdErrorCode.INVALID_TOKEN, detail);
	}
}
