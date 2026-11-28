package com.morak.feature.ad.infrastructure.ssv;

import com.morak.common.exception.BusinessException;
import com.morak.feature.ad.application.dto.result.AdSsvCallback;
import com.morak.feature.ad.application.port.out.AdSsvVerifier;
import com.morak.feature.ad.domain.exception.AdErrorCode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * AdMob 서버 측 확인(SSV) 알림의 서명을 확인한다.
 * 요청 쿼리는 "내용&signature=…&key_id=…" 모양이고, 서명은 "&signature=" 앞까지의 원문(디코딩 전)에 대한
 * ECDSA(SHA-256)다. signature와 key_id는 항상 마지막 두 개여야 한다. 그 뒤에 값을 덧붙여도 서명 검사를
 * 통과하므로, 다른 모양이면 거절한다.
 */
@Component
class GoogleAdSsvVerifier implements AdSsvVerifier {

	private static final int MAX_QUERY_LENGTH = 4096;
	private static final String SIGNATURE_MARK = "&signature=";
	private static final String KEY_ID_MARK = "&key_id=";

	private final VerifierKeys keys;

	GoogleAdSsvVerifier(VerifierKeys keys) {
		this.keys = keys;
	}

	@Override
	public AdSsvCallback verify(String rawQuery) {
		if (rawQuery == null || rawQuery.isBlank() || rawQuery.length() > MAX_QUERY_LENGTH) {
			throw invalid("쿼리 길이 오류 length=" + (rawQuery == null ? "null" : rawQuery.length()));
		}
		int signatureAt = rawQuery.indexOf(SIGNATURE_MARK);
		if (signatureAt < 0) {
			throw invalid("signature 없음");
		}
		String content = rawQuery.substring(0, signatureAt);
		String tail = rawQuery.substring(signatureAt);
		int keyIdAt = tail.indexOf(KEY_ID_MARK);
		if (keyIdAt < 0 || tail.indexOf('&', 1) != keyIdAt) {
			throw invalid("signature, key_id가 마지막 두 개가 아님");
		}
		String signature = tail.substring(SIGNATURE_MARK.length(), keyIdAt);
		String keyId = tail.substring(keyIdAt + KEY_ID_MARK.length());
		if (signature.isEmpty() || keyId.isEmpty() || keyId.indexOf('&') >= 0) {
			throw invalid("signature 또는 key_id 형식 오류");
		}

		PublicKey key = keys.find(keyId).orElseThrow(() -> invalid("모르는 key_id keyId=" + keyId));
		if (!isValidSignature(key, content, signature)) {
			throw invalid("서명 불일치 keyId=" + keyId);
		}
		return toCallback(parseParams(content));
	}

	private static boolean isValidSignature(PublicKey key, String content, String signature) {
		try {
			Signature verifier = Signature.getInstance("SHA256withECDSA");
			verifier.initVerify(key);
			verifier.update(content.getBytes(StandardCharsets.UTF_8));
			return verifier.verify(Base64.getUrlDecoder().decode(signature));
		} catch (IllegalArgumentException | GeneralSecurityException exception) {
			// 서명 문자열이 base64가 아니거나 DER 형식이 아닌 경우도 위조로 본다.
			return false;
		}
	}

	private static Map<String, String> parseParams(String content) {
		Map<String, String> params = new HashMap<>();
		for (String pair : content.split("&", -1)) {
			int equals = pair.indexOf('=');
			if (equals <= 0) {
				throw invalid("파라미터 형식 오류");
			}
			String name = pair.substring(0, equals);
			String value;
			try {
				value = URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
			} catch (IllegalArgumentException exception) {
				throw invalid("파라미터 디코딩 실패 name=" + name);
			}
			if (params.put(name, value) != null) {
				throw invalid("중복 파라미터 name=" + name);
			}
		}
		return params;
	}

	private static AdSsvCallback toCallback(Map<String, String> params) {
		String adUnit = required(params, "ad_unit");
		String userId = required(params, "user_id");
		String customData = required(params, "custom_data");
		String transactionId = required(params, "transaction_id");
		try {
			Instant rewardedAt = Instant.ofEpochMilli(Long.parseLong(required(params, "timestamp")));
			return new AdSsvCallback(adUnit, userId, customData, transactionId, rewardedAt);
		} catch (NumberFormatException exception) {
			throw invalid("timestamp 형식 오류");
		}
	}

	private static String required(Map<String, String> params, String name) {
		String value = params.get(name);
		if (value == null || value.isBlank()) {
			throw invalid("필수 파라미터 없음 name=" + name);
		}
		return value;
	}

	private static BusinessException invalid(String detail) {
		return new BusinessException(AdErrorCode.INVALID_SSV, detail);
	}
}
