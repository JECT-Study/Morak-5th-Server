package com.morak.feature.certification.application.service;

import com.morak.feature.certification.application.port.out.CertificationJudgeNotices;
import com.morak.feature.certification.application.port.out.CertificationJudgeStore;
import com.morak.feature.certification.application.port.out.CertificationJudgeStore.ApprovedDay;
import com.morak.feature.certification.application.port.out.CertificationJudgeTargets;
import com.morak.feature.certification.application.port.out.CertificationJudgeTargets.Member;
import com.morak.feature.certification.domain.vo.CertificationJudge;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 매일 12:00 KST 정오 자동 승인과 판정 경고(위키 §4.4, §4.9 ⑤). 판정 마감({@code judge_due_at})이 지난 {@code PENDING}을
 * {@code AUTO_APPROVED}로 바꾸고, 미처리한 담당자(방장·부방장 따로)에게 인증 날짜마다 1회 알림 #9를 보낸다. 누적 2회부터 팀 채팅에도 공개한다.
 * 포인트 챌린지 인증은 {@code judge_due_at}이 NULL이라 대상이 아니다.
 *
 * <p>경고 횟수는 저장하지 않고 {@code AUTO_APPROVED} 인증의 (판정 담당, 인증 날짜) 고유 개수로 센다(§4.4). 챌린지마다 한 트랜잭션이라 한
 * 건이 실패해도 그 챌린지만 롤백되고, 이미 자동 승인된 인증은 다시 바뀌지 않아 여러 번 돌려도 경고·채팅이 늘지 않는다.
 *
 * <p>TODO(D-42): 담당은 지금 role로 계산하므로 승계가 있으면 이전 담당의 경고도 새 담당에게 세어진다(정확히 하려면 D-7의
 * {@code judge_participation_id}가 필요하다). 담당자가 없으면(참여 중인 방장·부방장 없음) 경고를 건너뛴다.
 */
@Service
public class AutoApproveCertificationsService {

	private static final Logger log = LoggerFactory.getLogger(AutoApproveCertificationsService.class);
	/** 누적 2회부터 팀 채팅에 공개한다(위키 §4.4, D-6). */
	static final int TEAM_CHAT_FROM = 2;

	private final CertificationJudgeStore store;
	private final CertificationJudgeTargets targets;
	private final CertificationJudgeNotices notices;
	private final TransactionTemplate transaction;
	private final Clock clock;

	public AutoApproveCertificationsService(CertificationJudgeStore store, CertificationJudgeTargets targets,
			CertificationJudgeNotices notices, PlatformTransactionManager transactionManager, Clock clock) {
		this.store = store;
		this.targets = targets;
		this.notices = notices;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/** @return 자동 승인한 인증 수 */
	public int run() {
		Instant now = clock.instant();
		int approved = 0;
		for (long challengeId : store.findChallengesWithOverdue(now)) {
			try {
				approved += transaction.execute(status -> process(challengeId, now));
			} catch (RuntimeException exception) {
				log.error("정오 자동 승인 실패: challengeId={}", challengeId, exception);
			}
		}
		return approved;
	}

	private int process(long challengeId, Instant now) {
		List<ApprovedDay> approved = store.autoApprove(challengeId, now);
		if (approved.isEmpty()) {
			return 0;
		}
		List<ApprovedDay> all = store.findAutoApproved(challengeId);
		Map<Long, Member> submitters = targets.findParticipants(
				all.stream().map(ApprovedDay::participationId).distinct().toList());
		List<Member> members = targets.findMembers(challengeId);
		String title = targets.findChallenge(challengeId).map(CertificationJudgeTargets.Challenge::title).orElse("");

		for (CertificationJudge judge : CertificationJudge.values()) {
			TreeSet<LocalDate> newDays = daysOf(approved, judge, submitters);
			if (newDays.isEmpty()) {
				continue;
			}
			Optional<Member> recipient = members.stream()
					.filter(m -> judge.name().equals(m.role())
							&& JudgeCertificationService.JUDGE_STATUSES.contains(m.status()))
					.findFirst();
			if (recipient.isEmpty()) {
				log.warn("판정 경고 대상 없음: challengeId={}, judge={}, days={}", challengeId, judge, newDays);
				continue;
			}
			TreeSet<LocalDate> allDays = daysOf(all, judge, submitters);
			for (LocalDate day : newDays) {
				int count = allDays.headSet(day, true).size();
				boolean sent = notices.judgeOverdue(recipient.get().userId(), challengeId, title, judge, day, count);
				if (sent && count >= TEAM_CHAT_FROM) {
					notices.judgeOverdueToTeam(challengeId, judge, count);
				}
			}
		}
		log.info("정오 자동 승인: challengeId={}, count={}", challengeId, approved.size());
		return approved.size();
	}

	private static TreeSet<LocalDate> daysOf(List<ApprovedDay> rows, CertificationJudge judge,
			Map<Long, Member> submitters) {
		return rows.stream()
				.filter(row -> submitters.containsKey(row.participationId()))
				.filter(row -> CertificationJudge.forSubmitter(submitters.get(row.participationId()).role()) == judge)
				.map(ApprovedDay::certDate)
				.collect(Collectors.toCollection(TreeSet::new));
	}
}
