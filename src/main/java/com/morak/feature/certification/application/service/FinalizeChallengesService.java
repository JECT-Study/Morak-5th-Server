package com.morak.feature.certification.application.service;

import com.morak.feature.certification.application.port.out.CertificationFinalizeStore;
import com.morak.feature.certification.application.port.out.CertificationFinalizeStore.Member;
import com.morak.feature.certification.application.port.out.CertificationFinalizeTargets;
import com.morak.feature.certification.application.port.out.CertificationFinalizeTargets.Result;
import com.morak.feature.certification.domain.vo.CertificationTally;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 포인트 없는 챌린지의 결과 확정·종료(위키 §4.5 정산 실행 규칙, §4.9 ⑧). 종료일이 지난 IN_PROGRESS 챌린지를 FINALIZING으로 바꾸고,
 * FINALIZING에서 의무 기간 안의 판정 대기가 0건이면 같은 트랜잭션에서 남은 ACTIVE → COMPLETED, participation_result 저장,
 * 챌린지 ENDED로 확정한다. 조기 종료된 챌린지는 남은 사람이 EARLY_ENDED라 완주가 아니다. 확정 시각은
 * participation_result.finalized_at에 남고 채팅 종료(⑨)는 이 값으로 계산한다.
 *
 * <p>챌린지마다 한 트랜잭션이라 한 건이 실패하면 그 챌린지만 롤백돼 그 전 상태에 남고 다음 실행에서 다시 시도한다. ENDED는 대상에서
 * 빠지고 결과 행은 참가자마다 한 번만 저장돼 여러 번 돌려도 결과가 같다.
 *
 * <p>TODO(D-47): 실행 시각이 위키에 없어 12:05 퇴장 판정(⑥) 바로 뒤에 같은 스케줄러에서 돈다. 정오 자동 승인(⑤)으로 마지막 의무일
 * 인증까지 판정된 뒤라 정상 종료는 종료일 다음 날 12:05에, 조기 종료는 퇴장 당일 12:05에 확정된다. 판정 대기가 남으면 다음 날 다시 본다.
 * 포인트 챌린지는 정산과 같은 트랜잭션이어야 해서 포인트 담당 작업에서 이 흐름(잠금 → 집계 → 완주 → 결과 저장 → ENDED)에 정산을 붙인다.
 */
@Service
public class FinalizeChallengesService {

	private static final Logger log = LoggerFactory.getLogger(FinalizeChallengesService.class);
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final CertificationFinalizeStore store;
	private final CertificationFinalizeTargets targets;
	private final TransactionTemplate transaction;
	private final Clock clock;

	public FinalizeChallengesService(CertificationFinalizeStore store, CertificationFinalizeTargets targets,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.store = store;
		this.targets = targets;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/** @return 종료(ENDED)한 챌린지 수 */
	public int run() {
		Instant now = clock.instant();
		LocalDate today = LocalDate.ofInstant(now, KST);
		int ended = 0;
		for (long challengeId : store.findCandidates(today)) {
			try {
				if (Boolean.TRUE.equals(transaction.execute(status -> finalizeOne(challengeId, today, now)))) {
					ended++;
				}
			} catch (RuntimeException exception) {
				log.error("결과 확정 실패: challengeId={}", challengeId, exception);
			}
		}
		return ended;
	}

	private boolean finalizeOne(long challengeId, LocalDate today, Instant now) {
		targets.lockForFinalizing(challengeId, today);
		List<Member> members = store.findMembers(challengeId);
		int pending = members.stream().mapToInt(Member::pendingCount).sum();
		if (pending > 0) {
			log.info("결과 확정 보류(판정 대기): challengeId={}, pending={}", challengeId, pending);
			return false;
		}
		List<Result> results = members.stream().map(FinalizeChallengesService::toResult).toList();
		targets.completeActive(challengeId, now);
		targets.recordResults(results, now);
		targets.end(challengeId);
		log.info("결과 확정: challengeId={}, members={}", challengeId, members.size());
		return true;
	}

	/** 아직 ACTIVE인 사람만 완주다. 퇴장자·조기 종료 남은 사람(EARLY_ENDED)은 완주가 아니다(위키 §2, §4.4). */
	private static Result toResult(Member member) {
		CertificationTally tally = CertificationTally.of(member.startDate(), member.lastDay(), member.successDates());
		return new Result(member.participationId(), tally.approvedCount(), tally.totalCount(), tally.currentStreak(),
				tally.maxStreak(), member.active());
	}
}
