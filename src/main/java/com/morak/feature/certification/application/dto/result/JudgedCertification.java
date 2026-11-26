package com.morak.feature.certification.application.dto.result;

/** @param status APPROVED 또는 REJECTED */
public record JudgedCertification(long certificationId, String status) {}
