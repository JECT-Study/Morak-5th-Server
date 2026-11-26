package com.morak.feature.certification.application.port.out;

import com.morak.feature.certification.application.dto.result.CertificationReviewView.ChallengeOption;
import com.morak.feature.certification.application.dto.result.CertificationReviewView.Item;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 운영진 검수 대상(포인트 공개 챌린지의 제출 인증)을 읽고 판정을 기록하는 저장소. */
public interface CertificationReviewStore {

	List<Item> find(Filter filter, int limit);

	int count(Filter filter);

	List<ChallengeOption> findChallengeOptions();

	Optional<Row> findById(long certificationId);

	/**
	 * 아직 PENDING인 검수 대상 인증만 판정한다(상태 조건부 UPDATE). 이미 처리됐거나 검수 대상이 아니면 false다.
	 *
	 * @param rejectReason 거절일 때만 값이 있다
	 */
	boolean judge(long certificationId, String newStatus, long judgedBy, String rejectReason, Instant judgedAt);

	/** @param statuses 비어 있지 않아야 한다. certDate·challengeId는 null이면 조건에서 뺀다 */
	record Filter(LocalDate certDate, Long challengeId, Set<String> statuses) {}

	/** 상세 한 건. 사진은 저장 키로 담고 주소 변환은 서비스가 한다. */
	record Row(
			long certificationId,
			long challengeId,
			String challengeTitle,
			String submitterNickname,
			String submitterRole,
			LocalDate certDate,
			Instant submittedAt,
			String status,
			String commonMission,
			String verificationRules,
			List<String> imageKeys,
			String judgeNickname,
			Instant judgedAt,
			String rejectReason) {}
}
