package com.morak.feature.challenge.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.application.dto.result.DiscardedChallenge;
import com.morak.feature.challenge.application.port.out.ChallengeDiscardNotice;
import com.morak.feature.challenge.application.port.out.ChallengeParticipation.DiscardedParticipant;
import com.morak.feature.challenge.domain.enums.ChallengeStatus;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 방장의 시작 전 폐기(위키 §4.2). 상태 변경·포인트 반환·알림 #6을 한 트랜잭션에서 처리한다.
 * 이미 폐기된 챌린지를 다시 폐기하면 환불·알림 없이 같은 응답을 돌려준다.
 *
 * <p>신청이 바뀌어 롤백되면 {@link DiscardRetry}가 새 트랜잭션으로 다시 시도한다. 트랜잭션 밖에서 호출해야 한다.
 *
 * <p>TODO(D-26): 알림 #6은 방장을 뺀 대기·참여 중 참가자 전원에게 보낸다(위키는 "시작 대기 참여자"만 적음).
 */
@Service
public class DiscardChallengeService {

	private final DiscardChallengeTransaction transaction;
	private final Optional<ChallengeDiscardNotice> notice;
	private final TransactionTemplate discardTransaction;

	public DiscardChallengeService(DiscardChallengeTransaction transaction, Optional<ChallengeDiscardNotice> notice,
			PlatformTransactionManager transactionManager) {
		this.transaction = transaction;
		this.notice = notice;
		this.discardTransaction = new TransactionTemplate(transactionManager);
	}

	public DiscardedChallenge discard(long userId, long challengeId) {
		return DiscardRetry.execute(discardTransaction, challengeId, () -> discardOnce(userId, challengeId));
	}

	private DiscardedChallenge discardOnce(long userId, long challengeId) {
		DiscardChallengeTransaction.Result result = transaction.discard(challengeId, userId);
		if (!result.participants().isEmpty()) {
			ChallengeDiscardNotice notices = notice
					.orElseThrow(() -> new BusinessException(ChallengeErrorCode.DISCARD_INTEGRATION_UNAVAILABLE));
			for (DiscardedParticipant participant : result.participants()) {
				if (!participant.host()) {
					notices.discardedByHost(participant.userId(), challengeId, participant.participationId(),
							result.title(), participant.refundedPoint());
				}
			}
		}
		return new DiscardedChallenge(challengeId, ChallengeStatus.DISCARDED.name());
	}
}
