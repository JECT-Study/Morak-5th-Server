package com.morak.feature.certification.application.port.out;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 방장·부방장 판정과 정오 자동 승인이 챌린지·참가·유저 도메인에서 읽는 것들. */
public interface CertificationJudgeTargets {

	/** 챌린지가 없으면 empty. */
	Optional<Challenge> findChallenge(long challengeId);

	/** 참여 확정자(승인 대기·거절·퇴장 제외). 판정하는 사람을 찾는 데 쓴다. */
	List<Member> findMembers(long challengeId);

	/** participationId -> 참가자. 퇴장자도 포함하고, 없는 참가는 결과에 없다. 인증 제출자를 찾는 데 쓴다. */
	Map<Long, Member> findParticipants(Collection<Long> participationIds);

	/** userId -> 프로필. 탈퇴 등으로 없으면 결과에 없다. */
	Map<Long, Profile> findProfiles(Collection<Long> userIds);

	/** @param pointAmount 0보다 크면 운영진이 판정한다(위키 §4.4) */
	record Challenge(String title, LocalDate startDate, LocalDate endDate, int pointAmount) {}

	/** @param role HOST / DEPUTY / MEMBER, @param status participation.status */
	record Member(long participationId, long userId, String role, String status) {}

	record Profile(String nickname, String profileImageUrl) {}
}
