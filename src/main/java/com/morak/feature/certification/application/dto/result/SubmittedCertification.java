package com.morak.feature.certification.application.dto.result;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 제출한 인증.
 *
 * @param imageUrl 열람 주소. 주소를 만들 수 없으면(저장소 미설정) null
 */
public record SubmittedCertification(
		long certificationId, LocalDate certDate, String status, Instant submittedAt, String imageUrl) {}
