package com.morak.feature.certification.application.query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/** 다른 모듈(채팅 등)에 공개하는 인증 조회 API. */
public interface CertificationQuery {

	/** certificationId -> 인증. 지정한 챌린지의 인증만 반환하고, 없거나 다른 챌린지의 인증은 결과에 없다. */
	Map<Long, CertificationInfo> findInChallenge(Long challengeId, Collection<Long> certificationIds);

	record CertificationInfo(Long certificationId, Long participationId, LocalDate certDate) {}
}
