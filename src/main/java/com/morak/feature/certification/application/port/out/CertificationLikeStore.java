package com.morak.feature.certification.application.port.out;

import java.time.Instant;
import java.util.Optional;

/** 인증 좋아요(`certification_reaction`, reaction_type=LIKE). */
public interface CertificationLikeStore {

	/** 그 챌린지에 속한 인증의 status. 다른 챌린지의 인증이거나 없으면 empty. */
	Optional<String> findStatus(long challengeId, long certificationId);

	/** 이미 눌렀으면 아무것도 하지 않는다. 동시 요청은 uk_certification_reaction_user가 막는다. */
	void like(long certificationId, long userId, Instant likedAt);

	/** 누르지 않았으면 아무것도 하지 않는다. */
	void unlike(long certificationId, long userId);

	int count(long certificationId);
}
