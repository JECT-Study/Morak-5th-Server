package com.morak.feature.user.domain.vo;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import java.util.regex.Pattern;

public record Nickname(String value) {
	private static final Pattern FORMAT = Pattern.compile("[\uAC00-\uD7A3a-zA-Z0-9]{2,12}");

	public Nickname {
		if (value == null || !FORMAT.matcher(value).matches()) {
			throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
		}
	}
}
