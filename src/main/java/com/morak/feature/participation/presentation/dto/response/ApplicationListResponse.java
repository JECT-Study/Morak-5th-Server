package com.morak.feature.participation.presentation.dto.response;

import com.morak.feature.participation.application.dto.result.ApplicationView;
import java.util.List;

/** 참여 신청 목록 응답. */
public record ApplicationListResponse(List<ApplicationView> items) {

	public static ApplicationListResponse from(List<ApplicationView> items) {
		return new ApplicationListResponse(items);
	}
}
