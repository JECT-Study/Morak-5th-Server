package com.morak.feature.challenge.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.application.dto.CreateChallengeCommand;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class CreateChallengeService {

	private static final int MAX_CODE_ATTEMPTS = 5;
	private static final String INVITE_CODE_INDEX = "uk_challenge_invite_code";

	private final CreateChallengeTransaction transaction;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public CreateChallengeService(CreateChallengeTransaction transaction, Clock clock) {
		this.transaction = transaction;
		this.clock = clock;
	}

	/** 초대 코드가 중복된 경우에만 새 코드와 새 트랜잭션으로 개설을 재시도한다. */
	public CreatedChallenge create(long ownerId, CreateChallengeCommand command) {
		command.validateStartDate(clock.instant());
		for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
			String inviteCode = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
			try {
				return new CreatedChallenge(transaction.create(ownerId, command, inviteCode), inviteCode);
			} catch (DataIntegrityViolationException exception) {
				if (!isInviteCodeCollision(exception)) {
					throw exception;
				}
			}
		}
		throw new BusinessException(ChallengeErrorCode.INVITE_CODE_UNAVAILABLE);
	}

	/** 초대 코드 고유 제약 위반만 재시도 대상으로 구분한다. */
	private boolean isInviteCodeCollision(DataIntegrityViolationException exception) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation
					&& INVITE_CODE_INDEX.equals(violation.getConstraintName())) {
				return true;
			}
		}
		return false;
	}

	public record CreatedChallenge(long challengeId, String inviteCode) {}
}
