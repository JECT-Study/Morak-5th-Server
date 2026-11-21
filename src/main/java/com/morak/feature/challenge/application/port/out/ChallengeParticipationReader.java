package com.morak.feature.challenge.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ChallengeParticipationReader {

	/** 개설한 챌린지와 승인받아 참여 중인 챌린지를 합쳐 센다. */
	int countOngoing(long userId);

	/** 신청 이력이 있으면 현재 참가 상태를 반환한다. */
	Optional<String> findStatus(long challengeId, long userId);

	/** 내 참가 중 지정한 상태만 최근 신청 순으로 전부 반환한다. 홈 목록처럼 건수가 작은 경우에 쓴다. */
	List<MyParticipationRow> findMine(long userId, Set<String> statuses);

	/** 내 참가 중 지정한 상태만 participationId 내림차순 커서 페이지로 반환한다. */
	List<MyParticipationRow> findMine(long userId, Set<String> statuses, Long cursor, int limit);

	/** 시작 대기 중인 참가자(승인된 참여자와 승인 대기 신청자)에서 방장을 뺀 목록. 기간 재설정 알림 대상이다. */
	List<WaitingParticipant> findWaitingParticipants(long challengeId);

	/** 실제 합류한 인원(방장 포함, 승인 대기 제외). 시작 처리의 최소 인원 판단에 쓴다. */
	int countJoined(long challengeId);

	/** 주어진 시각 전에 합류한 인원(방장 포함, 승인 대기 제외). 시작 보류 챌린지가 추가 모집 마감까지 인원을 채웠는지 볼 때 쓴다. */
	int countJoinedBefore(long challengeId, Instant before);

	record WaitingParticipant(long participationId, long userId) {}

	/** 내 챌린지 목록에 필요한 참가 정보. role, status는 위키 §5 enum 문자열 그대로다. */
	record MyParticipationRow(long participationId, long challengeId, String role, String status) {}
}
