package com.morak.feature.challenge.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 폐기 트랜잭션을 다시 시도하는 공용 루프. 수동 폐기(방장)와 자동 폐기(스케줄러 ③)가 같이 쓴다.
 *
 * <p>포인트 계정을 잠그는 사이 포인트를 건 새 신청자가 끼어들면 트랜잭션 전체가 {@code CHALLENGE_DISCARD_APPLICATIONS_CHANGED}로
 * 롤백된다. 다시 시작하면 그 신청자의 계정까지 잠그므로, 새 트랜잭션으로 최대 {@value #MAX_ATTEMPTS}번까지 다시 시도한다.
 * 다른 예외는 바로 던진다. 트랜잭션 밖에서 호출해야 한다.
 *
 * <p>TODO(D-26): 재시도 횟수(3번)는 제안이다.
 */
final class DiscardRetry {

	private static final Logger log = LoggerFactory.getLogger(DiscardRetry.class);

	static final int MAX_ATTEMPTS = 3;

	private DiscardRetry() {}

	static <T> T execute(TransactionTemplate transaction, long challengeId, Supplier<T> work) {
		for (int attempt = 1; ; attempt++) {
			try {
				// execute는 실패한 트랜잭션을 롤백한 뒤에 예외를 던지므로, 다음 시도는 새 트랜잭션에서 처음부터 읽는다.
				return transaction.execute(status -> work.get());
			} catch (BusinessException exception) {
				if (exception.getErrorCode() != ChallengeErrorCode.DISCARD_APPLICATIONS_CHANGED
						|| attempt >= MAX_ATTEMPTS) {
					throw exception;
				}
				log.info("폐기 중 신청이 바뀌어 다시 시도: challengeId={}, attempt={}", challengeId, attempt);
			}
		}
	}
}
