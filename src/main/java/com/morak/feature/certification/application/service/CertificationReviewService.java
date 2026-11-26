package com.morak.feature.certification.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.certification.application.dto.result.CertificationReviewView.ChallengeSummary;
import com.morak.feature.certification.application.dto.result.CertificationReviewView.Detail;
import com.morak.feature.certification.application.dto.result.CertificationReviewView.Judge;
import com.morak.feature.certification.application.dto.result.CertificationReviewView.Page;
import com.morak.feature.certification.application.port.out.CertificationImageUrls;
import com.morak.feature.certification.application.port.out.CertificationJudgeNotices;
import com.morak.feature.certification.application.port.out.CertificationJudgeStore;
import com.morak.feature.certification.application.port.out.CertificationJudgeStore.JudgeRow;
import com.morak.feature.certification.application.port.out.CertificationJudgeTargets;
import com.morak.feature.certification.application.port.out.CertificationJudgeTargets.Challenge;
import com.morak.feature.certification.application.port.out.CertificationJudgeTargets.Member;
import com.morak.feature.certification.application.port.out.CertificationReviewStore;
import com.morak.feature.certification.application.port.out.CertificationReviewStore.Filter;
import com.morak.feature.certification.application.port.out.CertificationReviewStore.Row;
import com.morak.feature.certification.domain.exception.CertificationErrorCode;
import com.morak.feature.certification.domain.vo.ExpelThreshold;
import com.morak.feature.certification.domain.vo.RejectReason;
import com.morak.feature.certification.domain.vo.ReviewDeadline;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 운영진 인증 검수(위키 §4.10). 포인트 공개 챌린지에 제출된 인증을 한 건씩 승인·거절한다.
 *
 * <p>판정은 {@code status = 'PENDING'} 조건부 UPDATE 한 번이라 여러 운영진이 동시에 눌러도 먼저 처리된 판정이 유지되고,
 * 뒤의 요청은 409로 돌아간다(덮어쓰기 없음). 승인·거절의 성공·실패 실적은 인증 상태에서 계산하므로 따로 쌓지 않는다.
 *
 * <p>거절하면 방장·부방장 판정과 같이 제출자에게 알림 #12를 보낸다(위키 §4.8). 판정과 알림은 한 트랜잭션이다. 제출자가 이미 퇴장했으면
 * 집계에서 빠지는 인증이라 알림을 보내지 않는다(D-42 ③과 같은 이유, D-45).
 *
 * <p>TODO(D-31): 그 날짜의 미판정이 0건이 되면 퇴장 판정·결과 확정·정산을 이어서 실행한다. 해당 기능이 생기면 판정 직후에 호출한다.
 */
@Service
public class CertificationReviewService {

	private static final Logger log = LoggerFactory.getLogger(CertificationReviewService.class);

	static final String PENDING = "PENDING";
	static final String APPROVED = "APPROVED";
	static final String REJECTED = "REJECTED";

	private final CertificationReviewStore store;
	private final CertificationJudgeStore judgeStore;
	private final CertificationJudgeTargets targets;
	private final CertificationJudgeNotices notices;
	private final CertificationImageUrls imageUrls;
	private final TransactionTemplate transaction;
	private final Clock clock;

	public CertificationReviewService(CertificationReviewStore store, CertificationJudgeStore judgeStore,
			CertificationJudgeTargets targets, CertificationJudgeNotices notices, CertificationImageUrls imageUrls,
			TransactionTemplate transaction, Clock clock) {
		this.store = store;
		this.judgeStore = judgeStore;
		this.targets = targets;
		this.notices = notices;
		this.imageUrls = imageUrls;
		this.transaction = transaction;
		this.clock = clock;
	}

	/** 목록 상태 필터. 화면의 "미검수만 보기 / 전체 / 승인 / 거절"이다. */
	public enum StatusFilter {
		PENDING(Set.of("PENDING")),
		APPROVED(Set.of("APPROVED", "AUTO_APPROVED")),
		REJECTED(Set.of("REJECTED")),
		ALL(Set.of("PENDING", "APPROVED", "AUTO_APPROVED", "REJECTED"));

		private final Set<String> statuses;

		StatusFilter(Set<String> statuses) {
			this.statuses = statuses;
		}
	}

	@Transactional(readOnly = true)
	public Page list(LocalDate certDate, Long challengeId, StatusFilter statusFilter) {
		Filter filter = new Filter(certDate, challengeId, statusFilter.statuses);
		int pendingCount = store.count(new Filter(null, null, StatusFilter.PENDING.statuses));
		return new Page(store.find(filter, Page.LIMIT), store.count(filter), pendingCount,
				store.countOverdue(Instant.now(clock)));
	}

	/** 챌린지별 검수 현황. 목록 필터의 챌린지 선택지로도 쓴다. */
	@Transactional(readOnly = true)
	public List<ChallengeSummary> challengeSummaries() {
		return store.findChallengeSummaries(Instant.now(clock));
	}

	// 사진 서명(Supabase 호출) 동안 DB 연결을 쥐지 않으려고 트랜잭션을 두지 않는다. 제출된 인증의 사진은 바뀌지 않아 두 조회가 따로 읽어도 된다.
	public Detail detail(long certificationId) {
		return toDetail(find(certificationId));
	}

	// 판정은 조건부 UPDATE 한 번이라 트랜잭션이 필요 없다. 사진 서명(Supabase 호출) 동안 DB 연결·행 잠금을 쥐지 않으려고 트랜잭션을 두지 않는다.
	public Detail approve(long adminUserId, long certificationId) {
		judge(adminUserId, certificationId, APPROVED, null);
		return toDetail(find(certificationId));
	}

	/** 거절과 알림 #12를 한 트랜잭션에서 남긴다. 사진 서명은 트랜잭션 밖에서 한다. */
	public Detail reject(long adminUserId, long certificationId, String reason) {
		RejectReason rejectReason = new RejectReason(reason);
		transaction.executeWithoutResult(status -> {
			judge(adminUserId, certificationId, REJECTED, rejectReason.value());
			notifyRejected(certificationId);
		});
		return toDetail(find(certificationId));
	}

	/** 알림 #12: 퇴장 기준까지 남은 실패 횟수 = 퇴장 기준 − (거절·미제출 수). 방금 거절한 건이 이미 세어진다. */
	private void notifyRejected(long certificationId) {
		JudgeRow certification = judgeStore.findById(certificationId).orElseThrow(() -> new BusinessException(
				CertificationErrorCode.REVIEW_NOT_FOUND, "certificationId=" + certificationId));
		Challenge challenge = targets.findChallenge(certification.challengeId()).orElseThrow(() -> new BusinessException(
				CertificationErrorCode.REVIEW_NOT_FOUND, "challengeId=" + certification.challengeId()));
		Member submitter = targets.findParticipants(List.of(certification.participationId()))
				.get(certification.participationId());
		if (submitter == null || JudgeCertificationService.EXPELLED.equals(submitter.status())) {
			log.info("퇴장했거나 없는 제출자라 거절 알림을 보내지 않음: certificationId={}", certificationId);
			return;
		}
		int remaining = Math.max(0, ExpelThreshold.of(challenge.startDate(), challenge.endDate())
				- judgeStore.countFailures(certification.participationId()));
		notices.rejected(submitter.userId(), certification.challengeId(), certificationId, challenge.title(),
				certification.certDate(), remaining);
	}

	private void judge(long adminUserId, long certificationId, String newStatus, String rejectReason) {
		if (store.judge(certificationId, newStatus, adminUserId, rejectReason, Instant.now(clock))) {
			return;
		}
		// 갱신이 안 됐다면 없는 인증이거나, 다른 운영진이 먼저 처리한 인증이다. 후자는 상태 전이 위반이라 로그를 남긴다.
		Row current = find(certificationId);
		log.warn("이미 처리된 인증 판정 시도: certificationId={}, 현재 상태={}, 시도={}, adminUserId={}",
				certificationId, current.status(), newStatus, adminUserId);
		throw new BusinessException(CertificationErrorCode.ALREADY_JUDGED,
				"certificationId=" + certificationId + ", status=" + current.status());
	}

	private Row find(long certificationId) {
		return store.findById(certificationId)
				.orElseThrow(() -> new BusinessException(
						CertificationErrorCode.REVIEW_NOT_FOUND, "certificationId=" + certificationId));
	}

	private Detail toDetail(Row row) {
		boolean judged = !PENDING.equals(row.status());
		Judge judge = judged ? new Judge(row.judgeNickname(), row.judgedAt(), row.rejectReason()) : null;
		return new Detail(
				row.certificationId(), row.challengeId(), row.challengeTitle(), row.submitterNickname(),
				row.submitterRole(), row.certDate(), row.submittedAt(), row.status(), ReviewDeadline.of(row.certDate()),
				row.commonMission(), row.verificationRules(), imageUrls.resolve(row.imageKeys()), judge);
	}
}
