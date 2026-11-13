package com.morak.feature.challenge.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.application.dto.CreateChallengeCommand;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import com.morak.feature.challenge.domain.exception.InviteCodeConflictException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class CreateChallengeService {

	private static final int MAX_CODE_ATTEMPTS = 5;

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
			} catch (InviteCodeConflictException exception) {
				// 다음 시도에서 새 코드를 발급한다.
			}
		}
		throw new BusinessException(ChallengeErrorCode.INVITE_CODE_UNAVAILABLE);
	}

	public record CreatedChallenge(long challengeId, String inviteCode) {}
}
