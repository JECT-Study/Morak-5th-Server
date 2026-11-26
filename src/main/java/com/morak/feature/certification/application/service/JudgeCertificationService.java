package com.morak.feature.certification.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.certification.application.dto.result.JudgedCertification;
import com.morak.feature.certification.application.dto.result.PendingJudgment;
import com.morak.feature.certification.application.port.out.CertificationImageUrls;
import com.morak.feature.certification.application.port.out.CertificationJudgeNotices;
import com.morak.feature.certification.application.port.out.CertificationJudgeStore;
import com.morak.feature.certification.application.port.out.CertificationJudgeStore.JudgeRow;
import com.morak.feature.certification.application.port.out.CertificationJudgeStore.PendingRow;
import com.morak.feature.certification.application.port.out.CertificationJudgeTargets;
import com.morak.feature.certification.application.port.out.CertificationJudgeTargets.Challenge;
import com.morak.feature.certification.application.port.out.CertificationJudgeTargets.Member;
import com.morak.feature.certification.application.port.out.CertificationJudgeTargets.Profile;
import com.morak.feature.certification.domain.exception.CertificationErrorCode;
import com.morak.feature.certification.domain.vo.CertificationJudge;
import com.morak.feature.certification.domain.vo.ExpelThreshold;
import com.morak.feature.certification.domain.vo.RejectReason;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 없는 챌린지의 방장·부방장 인증 판정(위키 §4.4 판정 담당). 담당은 {@link CertificationJudge}로 계산한다.
 *
 * <p>판정은 운영진 검수(D-31)와 같이 {@code status = 'PENDING'} 조건부 UPDATE 한 번이라 먼저 처리된 판정이 유지된다. 판정 마감
 * ({@code judge_due_at}, 인증 다음 날 12:00 KST)이 지나면 정오 자동 승인 몫이라 판정할 수 없다. 거절하면 알림 #12를 보낸다.
 *
 * <p>TODO(D-42): 경로·응답, 판정할 수 있는 참가 상태(ACTIVE·EARLY_ENDED), 거절 사유 필수(운영진과 같음)는 제안값이다.
 */
@Service
public class JudgeCertificationService {

	private static final Logger log = LoggerFactory.getLogger(JudgeCertificationService.class);
	/** 조기 종료로 EARLY_ENDED가 돼도 남은 미판정 인증은 방장·부방장이 처리한다(위키 §4.4 조기 종료). */
	static final Set<String> JUDGE_STATUSES = Set.of("ACTIVE", "EARLY_ENDED");
	static final String PENDING = "PENDING";
	static final String APPROVED = "APPROVED";
	static final String REJECTED = "REJECTED";
	static final String EXPELLED = "EXPELLED";

	private final CertificationJudgeTargets targets;
	private final CertificationJudgeStore store;
	private final CertificationJudgeNotices notices;
	private final CertificationImageUrls imageUrls;
	private final Clock clock;

	public JudgeCertificationService(CertificationJudgeTargets targets, CertificationJudgeStore store,
			CertificationJudgeNotices notices, CertificationImageUrls imageUrls, Clock clock) {
		this.targets = targets;
		this.store = store;
		this.notices = notices;
		this.imageUrls = imageUrls;
		this.clock = clock;
	}

	/** 내가 지금 판정할 수 있는 인증(제출 순). 판정 담당이 아니거나 포인트 챌린지(운영진 몫)면 빈 목록이다. */
	public List<PendingJudgment> pending(long userId, long challengeId) {
		Challenge challenge = challenge(challengeId);
		Member judge = judge(challengeId, userId);
		if (challenge.pointAmount() > 0) {
			return List.of();
		}
		List<PendingRow> rows = store.findPending(challengeId, clock.instant());
		Map<Long, Member> submitters = targets.findParticipants(
				rows.stream().map(PendingRow::participationId).distinct().toList());
		List<PendingRow> mine = rows.stream()
				.filter(row -> judgeable(judge, submitters.get(row.participationId())))
				.toList();
		Map<Long, Profile> profiles = targets.findProfiles(
				mine.stream().map(row -> submitters.get(row.participationId()).userId()).distinct().toList());
		Map<String, String> urls = imageUrls.resolveByKey(
				mine.stream().map(PendingRow::imageKey).filter(Objects::nonNull).toList());
		return mine.stream().map(row -> {
			Member submitter = submitters.get(row.participationId());
			Profile profile = profiles.get(submitter.userId());
			return new PendingJudgment(row.certificationId(), submitter.userId(),
					profile == null ? null : profile.nickname(), profile == null ? null : profile.profileImageUrl(),
					submitter.role(), row.certDate(), row.submittedAt(), row.judgeDueAt(),
					row.imageKey() == null ? null : urls.get(row.imageKey()));
		}).toList();
	}

	@Transactional
	public JudgedCertification approve(long userId, long challengeId, long certificationId) {
		judge(userId, challengeId, certificationId, APPROVED, null);
		return new JudgedCertification(certificationId, APPROVED);
	}

	/** 거절과 알림 #12를 한 트랜잭션에서 남긴다. */
	@Transactional
	public JudgedCertification reject(long userId, long challengeId, long certificationId, String reason) {
		RejectReason rejectReason = new RejectReason(reason);
		Target target = judge(userId, challengeId, certificationId, REJECTED, rejectReason.value());
		int threshold = ExpelThreshold.of(target.challenge().startDate(), target.challenge().endDate());
		int remaining = Math.max(0, threshold - store.countFailures(target.submitter().participationId()));
		notices.rejected(target.submitter().userId(), challengeId, certificationId, target.challenge().title(),
				target.certification().certDate(), remaining);
		return new JudgedCertification(certificationId, REJECTED);
	}

	private Target judge(long userId, long challengeId, long certificationId, String newStatus, String reason) {
		Challenge challenge = challenge(challengeId);
		Member judge = judge(challengeId, userId);
		JudgeRow certification = store.findById(certificationId)
				.filter(row -> row.challengeId() == challengeId)
				.orElseThrow(() -> new BusinessException(
						CertificationErrorCode.JUDGMENT_NOT_FOUND, "certificationId=" + certificationId));
		Member submitter = targets.findParticipants(List.of(certification.participationId()))
				.get(certification.participationId());
		if (challenge.pointAmount() > 0 || !judgeable(judge, submitter)) {
			log.warn("판정 담당이 아닌 인증 판정 시도: certificationId={}, userId={}, role={}, pointAmount={}",
					certificationId, userId, judge.role(), challenge.pointAmount());
			throw new BusinessException(CertificationErrorCode.NOT_JUDGE, "certificationId=" + certificationId);
		}
		Instant now = clock.instant();
		if (!store.judge(certificationId, newStatus, userId, reason, now)) {
			// 이미 판정·자동 승인됐거나 마감이 지났다. 둘 다 상태 전이 위반이라 로그를 남긴다.
			JudgeRow current = store.findById(certificationId).orElse(certification);
			log.warn("판정할 수 없는 인증 판정 시도: certificationId={}, 상태={}, 마감={}, 시도={}, userId={}",
					certificationId, current.status(), current.judgeDueAt(), newStatus, userId);
			throw new BusinessException(PENDING.equals(current.status())
					? CertificationErrorCode.JUDGE_DUE_PASSED : CertificationErrorCode.ALREADY_JUDGED,
					"certificationId=" + certificationId + ", status=" + current.status());
		}
		return new Target(challenge, submitter, certification);
	}

	// 퇴장자의 남은 인증은 집계에서 빠지므로(D-12) 판정하지 않는다. 거절하면 퇴장자에게 실패 알림이 가기 때문이다.
	private static boolean judgeable(Member judge, Member submitter) {
		return submitter != null && !EXPELLED.equals(submitter.status())
				&& CertificationJudge.canJudge(judge.role(), submitter.role());
	}

	private Challenge challenge(long challengeId) {
		return targets.findChallenge(challengeId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
	}

	private Member judge(long challengeId, long userId) {
		return targets.findMembers(challengeId).stream()
				.filter(member -> member.userId() == userId && JUDGE_STATUSES.contains(member.status()))
				.findFirst()
				.orElseThrow(() -> {
					log.warn("참여 중이 아닌 사용자의 판정 접근: challengeId={}, userId={}", challengeId, userId);
					return new BusinessException(CertificationErrorCode.NOT_JUDGE, "challengeId=" + challengeId);
				});
	}

	private record Target(Challenge challenge, Member submitter, JudgeRow certification) {}
}
