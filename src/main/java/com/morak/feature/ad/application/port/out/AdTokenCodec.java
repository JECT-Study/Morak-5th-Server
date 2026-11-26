package com.morak.feature.ad.application.port.out;

import com.morak.feature.ad.domain.vo.AdToken;

public interface AdTokenCodec {

	/** 서명한 토큰 문자열을 만든다. */
	String issue(AdToken token);

	/** 서명을 검증하고 내용을 읽는다. 형식이 틀리거나 서명이 맞지 않으면 AD_INVALID_TOKEN. */
	AdToken parse(String raw);
}
