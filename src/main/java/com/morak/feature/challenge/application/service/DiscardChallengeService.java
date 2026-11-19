package com.morak.feature.challenge.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.application.dto.result.DiscardedChallenge;
import com.morak.feature.challenge.application.port.out.ChallengeDiscardNotice;
import com.morak.feature.challenge.application.port.out.ChallengeParticipation.DiscardedParticipant;
import com.morak.feature.challenge.domain.enums.ChallengeStatus;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 방장의 시작 전 폐기(위키 §4.2). 상태 변경·포인트 반환·알림 #6을 한 트랜잭션에서 처리한다.
 * 이미 폐기된 챌린지를 다시 폐기하면 환불·알림 없이 같은 응답을 돌려준다.
 *
 * <p>포인트 계정을 잠그는 사이 포인트를 건 새 신청자가 끼어들면 트랜잭션 전체가 409로 롤백된다. 다시 시작하면 그 신청자의
 * 계정까지 잠그므로, 새 트랜잭션으로 최대 {@value #MAX_ATTEMPTS}번까지 다시 시도한다. 트랜잭션 밖에서 호출해야 한다.
 *
 * <p>TODO(D-26): 알림 #6은 방장을 뺀 대기·참여 중 참가자 전원에게 보낸다(위키는 "시작 대기 참여자"만 적음).
 * 재시도 횟수(3번)도 제안이다.
 */
@Service
public class DiscardChallengeService {

	private static final Logger log = LoggerFactory.getLogger(DiscardChallengeService.class);

	static final int MAX_ATTEMPTS = 3;

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
		for (int attempt = 1; ; attempt++) {
			try {
				// execute는 실패한 트랜잭션을 롤백한 뒤에 예외를 던지므로, 다음 시도는 새 트랜잭션에서 처음부터 읽는다.
				return discardTransaction.execute(status -> discardOnce(userId, challengeId));
			} catch (BusinessException exception) {
				if (exception.getErrorCode() != ChallengeErrorCode.DISCARD_APPLICATIONS_CHANGED
						|| attempt >= MAX_ATTEMPTS) {
					throw exception;
				}
				log.info("폐기 중 신청이 바뀌어 다시 시도: challengeId={}, attempt={}", challengeId, attempt);
			}
		}
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
