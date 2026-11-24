package com.morak.feature.certification.domain.vo;

import com.morak.common.exception.BusinessException;
import com.morak.feature.certification.domain.exception.CertificationErrorCode;

/** 운영진이 입력하는 인증 거절 사유. 제출자에게 공개되므로 비어 있을 수 없고, certification.reject_reason(200자)에 맞춘다. */
public record RejectReason(String value) {

	public static final int MAX_LENGTH = 200;

	public RejectReason {
		String trimmed = value == null ? "" : value.strip();
		if (trimmed.isEmpty()) {
			throw new BusinessException(CertificationErrorCode.REJECT_REASON_REQUIRED);
		}
		if (trimmed.codePointCount(0, trimmed.length()) > MAX_LENGTH) {
			throw new BusinessException(CertificationErrorCode.REJECT_REASON_TOO_LONG);
		}
		value = trimmed;
	}
}
