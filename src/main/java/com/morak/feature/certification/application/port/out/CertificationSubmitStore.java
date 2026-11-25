package com.morak.feature.certification.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/** 제출한 인증과 사진을 저장한다. */
public interface CertificationSubmitStore {

	boolean exists(long participationId, LocalDate certDate);

	/**
	 * 인증(PENDING)과 사진 한 장을 한 트랜잭션으로 저장한다.
	 *
	 * @param judgeDueAt 운영진 판정이면 null
	 * @return 새 인증 ID. 같은 참가자·날짜의 인증이 이미 있으면 empty(아무것도 저장하지 않는다)
	 */
	Optional<Long> save(long challengeId, long participationId, LocalDate certDate, Instant submittedAt,
			Instant judgeDueAt, String imageKey);
}
