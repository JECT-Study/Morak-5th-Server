package com.morak.feature.challenge.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.challenge.application.command.ChallengeCommand;
import com.morak.feature.challenge.domain.entity.Challenge;
import com.morak.feature.challenge.domain.enums.ChallengeStatus;
import com.morak.feature.challenge.domain.repository.ChallengeRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class ChallengeCommandService implements ChallengeCommand {

	private final ChallengeRepository challenges;

	ChallengeCommandService(ChallengeRepository challenges) {
		this.challenges = challenges;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ChallengeForApplication> find(long challengeId) {
		return challenges.findById(challengeId).map(ChallengeCommandService::toInfo);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<ChallengeForApplication> lockForUpdate(long challengeId) {
		return challenges.findByIdForUpdate(challengeId).map(ChallengeCommandService::toInfo);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void acceptMember(long challengeId) {
		Challenge challenge = challenges.findByIdForUpdate(challengeId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		challenge.acceptMember();
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void changeOwner(long challengeId, long newOwnerUserId) {
		Challenge challenge = challenges.findByIdForUpdate(challengeId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		challenge.changeOwner(newOwnerUserId);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void endEarly(long challengeId, LocalDate today) {
		Challenge challenge = challenges.findByIdForUpdate(challengeId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		challenge.endEarly(today);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void lockForFinalizing(long challengeId, LocalDate today) {
		Challenge challenge = challenges.findByIdForUpdate(challengeId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		if (challenge.getStatus() != ChallengeStatus.FINALIZING) {
			challenge.beginFinalizing(today); // IN_PROGRESS가 아니면 전이 위반으로 로그를 남기고 거절한다.
		}
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void end(long challengeId) {
		Challenge challenge = challenges.findByIdForUpdate(challengeId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		challenge.end();
	}

	private static ChallengeForApplication toInfo(Challenge challenge) {
		return new ChallengeForApplication(
				challenge.getId(),
				challenge.getOwnerUserId(),
				challenge.getTitle(),
				challenge.getVisibility().name(),
				challenge.getInviteCode(),
				challenge.isRecruiting(),
				challenge.getCapacity(),
				challenge.getCurrentCount(),
				challenge.getPointAmount());
	}
}
