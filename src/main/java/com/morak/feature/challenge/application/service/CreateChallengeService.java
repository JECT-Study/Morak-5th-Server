package com.morak.feature.challenge.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.application.dto.command.CreateChallengeCommand;
import com.morak.feature.challenge.application.dto.result.CreatedChallenge;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import com.morak.feature.challenge.domain.exception.InviteCodeConflictException;
import com.morak.feature.challenge.domain.policy.InviteCodePolicy;
import java.security.SecureRandom;
import java.time.Clock;
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
		return create(ownerId, command, null);
	}

	CreatedChallenge create(long ownerId, CreateChallengeCommand command, String coverImageKey) {
		command.validateStartDate(clock.instant());
		for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
			String inviteCode = InviteCodePolicy.generate(random);
			try {
				long id = transaction.create(ownerId, command, inviteCode, coverImageKey);
				return new CreatedChallenge(id, inviteCode);
			} catch (InviteCodeConflictException exception) {
				// 다음 시도에서 새 코드를 발급한다.
			}
		}
		throw new BusinessException(ChallengeErrorCode.INVITE_CODE_UNAVAILABLE);
	}
}
