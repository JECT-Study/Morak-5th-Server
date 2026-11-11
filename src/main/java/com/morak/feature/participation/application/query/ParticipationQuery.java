package com.morak.feature.participation.application.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 다른 모듈(채팅, 챌린지 등)에 공개하는 참가자 조회 API.
 *
 * <p>status, role 값은 위키 §5 enum 문자열 그대로다. 닉네임·프로필 이미지는 user 도메인 소유이므로 여기서 제공하지 않고,
 * 호출하는 쪽이 {@code userId}로 UserQuery에서 가져온다.
 */
public interface ParticipationQuery {

	/** 챌린지에 참여 중(ACTIVE)이거나 퇴장 공지를 받았거나(EXPEL_NOTICED) 완주(COMPLETED)한 참가자. 없으면 empty. */
	Optional<ParticipantInfo> findJoinedParticipant(Long challengeId, Long userId);

	/** participationId -> 참가자. 존재하지 않는 id는 결과에 없다. */
	Map<Long, ParticipantInfo> findParticipantsByIds(Collection<Long> participationIds);

	// ---- 챌린지 상세·목록: 인원 현황 ----

	/** 현재 참여 인원: ACTIVE + EXPEL_NOTICED. 방장 포함, 승인 대기(PENDING)는 제외한다. */
	int countJoined(Long challengeId);

	/** 목록 화면용 일괄 조회. challengeId -> 현재 참여 인원이며 참여자가 없는 챌린지도 0으로 채운다. */
	Map<Long, Integer> countJoinedByChallengeIds(Collection<Long> challengeIds);

	/** 챌린지의 참가 상태별 인원. 해당 상태의 참가자가 없으면 키가 없다. 시작·조기 종료 판단과 승인 대기 건수 표시에 쓴다. */
	Map<String, Integer> countByStatus(Long challengeId);

	// ---- 챌린지 상세: 참여자 목록 ----

	/**
	 * 참여 확정자(ACTIVE, EXPEL_NOTICED, COMPLETED, EARLY_ENDED) 목록. 합류 순서(joined_at, id)로 정렬한다.
	 * 방장·부방장 식별은 role로 한다.
	 */
	List<ParticipantSummary> findMembers(Long challengeId);

	/** 지정한 상태의 참가자 목록(합류·신청 순). 시작 전 폐기 대상, 알림 수신 대상 조회처럼 상태 조합이 호출처마다 다를 때 쓴다. */
	List<ParticipantSummary> findParticipants(Long challengeId, Set<String> statuses);

	// ---- 방장 신청 관리 ----

	/** 승인 대기(PENDING) 신청 목록. 오래된 신청부터. */
	List<Application> findPendingApplications(Long challengeId);

	// ---- 내 참가 상태 ----

	/** 내가 이 챌린지에 낸 신청·참여 정보. 상태와 무관하게 반환한다(재지원 불가 판단에 쓴다). 없으면 empty. */
	Optional<MyParticipation> findMyParticipation(Long challengeId, Long userId);

	/** 내 참가 목록. 지정한 상태만 최근 신청 순으로 반환한다. */
	List<MyParticipation> findMyParticipations(Long userId, Set<String> statuses);

	/**
	 * 동시 참여 한도(5개) 판단용 건수. 상태가 ACTIVE·EXPEL_NOTICED인 참가 중 챌린지가 RECRUITING·START_HELD·IN_PROGRESS인 것만
	 * 센다. 승인 대기와 종료된 방은 제외한다. 위키 §4.3 구현 해석.
	 */
	int countOngoing(Long userId);

	// ---- 종료 결과 ----

	/** 챌린지 종료 후 확정된 참가자별 결과. 인증률이 높은 순. 확정 전이면 비어 있다. */
	List<ParticipantResult> findResults(Long challengeId);

	record ParticipantInfo(Long participationId, Long userId, String role, String status) {

		public boolean isActive() {
			return "ACTIVE".equals(status);
		}
	}

	/** 참여자 목록 한 행. */
	record ParticipantSummary(Long participationId, Long userId, String role, String status, Instant joinedAt) {}

	/** 방장이 보는 승인 대기 신청. */
	record Application(Long participationId, Long userId, String applyMessage, Instant appliedAt) {}

	/** 로그인한 사용자 본인의 참가 정보. 상세 화면의 버튼 상태와 내 챌린지 목록에 쓴다. */
	record MyParticipation(
			Long participationId,
			Long challengeId,
			String role,
			String status,
			String applyMessage,
			Instant appliedAt,
			Instant joinedAt,
			Instant endedAt,
			String endReason) {}

	/** 종료 확정된 참가자 결과(participation_result). */
	record ParticipantResult(
			Long participationId,
			Long userId,
			int approvedCount,
			int totalCount,
			BigDecimal achievementRate,
			int currentStreak,
			int maxStreak,
			boolean completed,
			Instant finalizedAt) {}
}
