package com.morak.feature.certification.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** 인증 피드가 챌린지·참가·유저 도메인에서 읽는 것들. */
public interface CertificationFeedTargets {

	boolean challengeExists(long challengeId);

	/** 그 챌린지의 참여 확정자(승인 대기·거절·퇴장 제외) 중 조회자. 없으면 empty. */
	Optional<Viewer> findViewer(long challengeId, long userId);

	/** participationId -> 제출자. 없는 참가는 결과에 없다. */
	Map<Long, Submitter> findSubmitters(Collection<Long> participationIds);

	/** @param status participation.status */
	record Viewer(long participationId, String status) {}

	/** @param role HOST / DEPUTY / MEMBER. 탈퇴한 사용자는 nickname·profileImageUrl이 null이다 */
	record Submitter(long userId, String role, String nickname, String profileImageUrl) {}
}
