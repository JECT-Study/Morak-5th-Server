package com.morak.feature.challenge.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.application.port.out.ChallengeDiscardNotice;
import com.morak.feature.challenge.application.port.out.ChallengeParticipation.DiscardedParticipant;
import com.morak.feature.challenge.application.service.DiscardChallengeTransaction.Outcome;
import com.morak.feature.challenge.application.service.DiscardChallengeTransaction.Result;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import com.morak.feature.challenge.domain.repository.ChallengeRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 추가 모집 마감이 지난 시작 보류 챌린지를 폐기하는 일일 배치(위키 §4.9 ③). 몇 번 돌려도 결과가 같다.
 * 챌린지마다 폐기·포인트 반환·알림 #7을 한 트랜잭션으로 처리하므로 한 건이 실패해도 그 건만 롤백되고 나머지는 계속 처리한다.
 * 실패한 챌린지는 다음 실행에서 다시 후보가 된다. 이 클래스에는 트랜잭션을 걸지 않는다(걸면 한 건 실패가 전체를 되돌린다).
 *
 * <p>건너뛴 챌린지(재설정·마감 전 인원 충족)는 id 순으로 이미 지나갔으므로 다시 읽지 않는다.
 * TODO(D-30): 알림 #7은 방장을 포함한 폐기 참가자(승인 대기·참여 중) 전원에게 보내고, 알림이 실패하면 그 챌린지의 폐기도 롤백한다.
 * 페이지 크기(100)는 시작 처리와 같은 제안이다.
 */
@Service
public class DiscardExpiredChallengesService {

	private static final Logger log = LoggerFactory.getLogger(DiscardExpiredChallengesService.class);
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	static final int PAGE_SIZE = 100;

	private final ChallengeRepository challenges;
	private final DiscardChallengeTransaction transaction;
	private final Optional<ChallengeDiscardNotice> notice;
	private final TransactionTemplate discardTransaction;
	private final Clock clock;
	private final int pageSize;

	@Autowired
	public DiscardExpiredChallengesService(ChallengeRepository challenges, DiscardChallengeTransaction transaction,
			Optional<ChallengeDiscardNotice> notice, PlatformTransactionManager transactionManager, Clock clock) {
		this(challenges, transaction, notice, transactionManager, clock, PAGE_SIZE);
	}

	DiscardExpiredChallengesService(ChallengeRepository challenges, DiscardChallengeTransaction transaction,
			Optional<ChallengeDiscardNotice> notice, PlatformTransactionManager transactionManager, Clock clock,
			int pageSize) {
		this.challenges = challenges;
		this.transaction = transaction;
		this.notice = notice;
		this.discardTransaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
		this.pageSize = pageSize;
	}

	/** 지금 시각의 KST 날짜로 실행한다. */
	public Summary run() {
		return runFor(LocalDate.ofInstant(clock.instant(), KST));
	}

	/** 주어진 KST 날짜를 오늘로 보고 실행한다. */
	public Summary runFor(LocalDate today) {
		int discarded = 0;
		int skipped = 0;
		int failed = 0;
		long afterId = 0;
		while (true) {
			List<Long> ids = challenges.findDiscardDueIds(today, afterId, pageSize);
			for (long challengeId : ids) {
				try {
					Outcome outcome = DiscardRetry.execute(discardTransaction, challengeId,
							() -> discardOnce(challengeId, today));
					if (outcome == Outcome.DISCARDED) {
						discarded++;
						log.info("챌린지 자동 폐기: challengeId={}, today={}", challengeId, today);
					} else {
						skipped++;
					}
				} catch (RuntimeException exception) {
					failed++;
					log.error("챌린지 자동 폐기 실패: challengeId={}, today={}", challengeId, today, exception);
				}
			}
			if (ids.size() < pageSize) {
				break;
			}
			afterId = ids.getLast();
		}
		Summary summary = new Summary(discarded, skipped, failed);
		if (failed > 0) {
			log.error("챌린지 자동 폐기 완료(실패 있음): today={}, {}", today, summary);
		} else {
			log.info("챌린지 자동 폐기 완료: today={}, {}", today, summary);
		}
		return summary;
	}

	private Outcome discardOnce(long challengeId, LocalDate today) {
		Result result = transaction.discardIfExpired(challengeId, today);
		if (!result.participants().isEmpty()) {
			ChallengeDiscardNotice notices = notice
					.orElseThrow(() -> new BusinessException(ChallengeErrorCode.DISCARD_INTEGRATION_UNAVAILABLE));
			for (DiscardedParticipant participant : result.participants()) {
				notices.autoDiscarded(participant.userId(), challengeId, participant.participationId(), result.title(),
						participant.refundedPoint());
			}
		}
		return result.outcome();
	}

	public record Summary(int discarded, int skipped, int failed) {}
}
