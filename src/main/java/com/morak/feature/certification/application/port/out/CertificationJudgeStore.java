package com.morak.feature.certification.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 포인트 없는 챌린지 인증의 방장·부방장 판정과 정오 자동 승인 저장소. 판정 마감은 {@code judge_due_at}이다(D-34 ⑤). */
public interface CertificationJudgeStore {

	/** 그 챌린지의 아직 판정할 수 있는 인증(PENDING, 마감 전). 제출 순. */
	List<PendingRow> findPending(long challengeId, Instant now);

	Optional<JudgeRow> findById(long certificationId);

	/** PENDING이고 판정 마감 전인 인증만 판정한다(상태 조건부 UPDATE). 아니면 false다. */
	boolean judge(long certificationId, String newStatus, long judgedBy, String rejectReason, Instant now);

	/** 그 참가자의 누적 실패(거절·미제출) 수(위키 §4.4 fail_count). */
	int countFailures(long participationId);

	/** 판정 마감이 지난 PENDING 인증이 있는 챌린지. id 순. */
	List<Long> findChallengesWithOverdue(Instant now);

	/**
	 * 그 챌린지의 판정 마감이 지난 PENDING 인증을 AUTO_APPROVED로 바꾸고, 바뀐 인증 중 판정 경고에 셀 것을 돌려준다.
	 * 제출자가 그 인증 날짜나 그 전에 퇴장했으면(퇴장일 제출) 판정할 수 없었으므로 빠진다.
	 */
	List<ApprovedDay> autoApprove(long challengeId, Instant now);

	/** 그 챌린지의 AUTO_APPROVED 인증 중 판정 경고에 셀 것(퇴장일 제출 제외). 경고 횟수 계산에 쓴다(위키 §4.4). */
	List<ApprovedDay> findAutoApproved(long challengeId);

	/** @param imageKey 사진이 없으면 null */
	record PendingRow(long certificationId, long participationId, LocalDate certDate, Instant submittedAt,
			Instant judgeDueAt, String imageKey) {}

	/** @param judgeDueAt 포인트 챌린지(운영진 검수)면 null */
	record JudgeRow(long certificationId, long challengeId, long participationId, LocalDate certDate, String status,
			Instant judgeDueAt) {}

	record ApprovedDay(long participationId, LocalDate certDate) {}
}
