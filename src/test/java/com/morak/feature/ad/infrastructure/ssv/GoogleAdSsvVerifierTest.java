package com.morak.feature.ad.infrastructure.ssv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.morak.common.exception.BusinessException;
import com.morak.feature.ad.application.dto.result.AdSsvCallback;
import com.morak.feature.ad.domain.exception.AdErrorCode;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GoogleAdSsvVerifierTest {

	private static final String KEY_ID = "3335741209";
	private static final String CONTENT = "ad_network=5450213213286189855&ad_unit=1234567890"
			+ "&custom_data=abc.def&reward_amount=1&reward_item=point&timestamp=1795000000000"
			+ "&transaction_id=TX-1&user_id=10";

	private static KeyPair trusted;
	private static KeyPair stranger;

	private final GoogleAdSsvVerifier verifier = new GoogleAdSsvVerifier(
			keyId -> KEY_ID.equals(keyId) ? Optional.of(trusted.getPublic()) : Optional.empty());

	@BeforeAll
	static void generateKeys() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
		generator.initialize(new ECGenParameterSpec("secp256r1"));
		trusted = generator.generateKeyPair();
		stranger = generator.generateKeyPair();
	}

	@Test
	void validSignatureReturnsCallback() throws Exception {
		AdSsvCallback callback = verifier.verify(signed(CONTENT, trusted, KEY_ID));

		assertEquals("1234567890", callback.adUnit());
		assertEquals("10", callback.userId());
		assertEquals("abc.def", callback.customData());
		assertEquals("TX-1", callback.transactionId());
		assertEquals(Instant.ofEpochMilli(1_795_000_000_000L), callback.rewardedAt());
	}

	@Test
	void urlEncodedValuesAreDecodedAfterTheSignatureIsChecked() throws Exception {
		String content = CONTENT.replace("custom_data=abc.def", "custom_data=a%2Bb%3Dc");

		AdSsvCallback callback = verifier.verify(signed(content, trusted, KEY_ID));

		assertEquals("a+b=c", callback.customData());
	}

	@Test
	void tamperedContentIsRejected() throws Exception {
		String query = signed(CONTENT, trusted, KEY_ID).replace("user_id=10", "user_id=99");

		assertInvalid(query);
	}

	@Test
	void signatureFromAnotherKeyIsRejected() throws Exception {
		assertInvalid(signed(CONTENT, stranger, KEY_ID));
	}

	@Test
	void unknownKeyIdIsRejected() throws Exception {
		assertInvalid(signed(CONTENT, trusted, "1"));
	}

	@Test
	void parameterAppendedAfterKeyIdIsRejected() throws Exception {
		assertInvalid(signed(CONTENT, trusted, KEY_ID) + "&user_id=99");
	}

	@Test
	void keyIdBeforeSignatureIsRejected() throws Exception {
		String signature = sign(CONTENT, trusted);

		assertInvalid(CONTENT + "&key_id=" + KEY_ID + "&signature=" + signature);
	}

	@Test
	void duplicateParameterIsRejected() throws Exception {
		assertInvalid(signed(CONTENT + "&user_id=99", trusted, KEY_ID));
	}

	@Test
	void missingRequiredParameterIsRejected() throws Exception {
		assertInvalid(signed(CONTENT.replace("&custom_data=abc.def", ""), trusted, KEY_ID));
	}

	@Test
	void nonNumericTimestampIsRejected() throws Exception {
		assertInvalid(signed(CONTENT.replace("1795000000000", "yesterday"), trusted, KEY_ID));
	}

	@Test
	void malformedSignatureIsRejected() {
		assertInvalid(CONTENT + "&signature=!!!&key_id=" + KEY_ID);
	}

	@Test
	void missingOrBlankQueryIsRejected() {
		assertInvalid(null);
		assertInvalid("  ");
		assertInvalid(CONTENT);
	}

	@Test
	void keyLookupFailureIsReportedAsUnavailable() throws Exception {
		GoogleAdSsvVerifier broken = new GoogleAdSsvVerifier(keyId -> {
			throw new BusinessException(AdErrorCode.UNAVAILABLE, "down");
		});

		BusinessException exception =
				assertThrows(BusinessException.class, () -> broken.verify(signed(CONTENT, trusted, KEY_ID)));
		assertEquals(AdErrorCode.UNAVAILABLE, exception.getErrorCode());
	}

	private void assertInvalid(String query) {
		BusinessException exception = assertThrows(BusinessException.class, () -> verifier.verify(query));
		assertEquals(AdErrorCode.INVALID_SSV, exception.getErrorCode());
	}

	private static String signed(String content, KeyPair signer, String keyId) throws Exception {
		return content + "&signature=" + sign(content, signer) + "&key_id=" + keyId;
	}

	private static String sign(String content, KeyPair signer) throws Exception {
		Signature signature = Signature.getInstance("SHA256withECDSA");
		signature.initSign(signer.getPrivate());
		signature.update(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
	}
}
