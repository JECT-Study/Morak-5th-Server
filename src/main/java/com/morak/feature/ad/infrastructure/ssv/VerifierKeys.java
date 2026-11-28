package com.morak.feature.ad.infrastructure.ssv;

import java.security.PublicKey;
import java.util.Optional;

/** SSV 서명을 확인하는 구글 공개키 보관소. */
interface VerifierKeys {

	/** keyId에 맞는 공개키. 구글이 모르는 키 ID면 비어 있다. 키 목록을 가져오지 못하면 예외. */
	Optional<PublicKey> find(String keyId);
}
