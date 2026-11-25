package com.morak.feature.certification.application.dto.result;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 제출한 인증.
 *
 * @param imageUrl 열람용 서명 주소(300초). 서명에 실패하면 null
 */
public record SubmittedCertification(
		long certificationId, LocalDate certDate, String status, Instant submittedAt, String imageUrl) {}
