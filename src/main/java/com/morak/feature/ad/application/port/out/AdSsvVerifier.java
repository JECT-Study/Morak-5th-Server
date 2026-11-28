package com.morak.feature.ad.application.port.out;

import com.morak.feature.ad.application.dto.result.AdSsvCallback;

public interface AdSsvVerifier {

	/**
	 * 구글 서명을 확인하고 내용을 읽는다. rawQuery는 디코딩하지 않은 요청 쿼리 문자열 그대로다.
	 * 서명이 맞지 않거나 형식이 틀리면 AD_INVALID_SSV, 서명 키를 가져오지 못하면 AD_UNAVAILABLE.
	 */
	AdSsvCallback verify(String rawQuery);
}
