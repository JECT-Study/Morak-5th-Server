package com.morak.feature.challenge.application.dto.result;

import java.time.LocalDate;

/** 기간 재설정 결과. 같은 기간으로 다시 호출해도 같은 값이다. */
public record RescheduledChallenge(long challengeId, String status, LocalDate startDate, LocalDate endDate) {}
