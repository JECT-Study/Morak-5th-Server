package com.morak.feature.participation.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.participation.application.command.ParticipationCommand;
import com.morak.feature.participation.application.port.out.ApplicationChallenges;
import com.morak.feature.participation.domain.entity.Participation;
import com.morak.feature.participation.domain.enums.ParticipationRole;
import com.morak.feature.participation.domain.repository.ParticipationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class ParticipationCommandService implements ParticipationCommand {

	private static final Logger log = LoggerFactory.getLogger(ParticipationCommandService.class);

	private final ParticipationRepository participations;
	private final ApplicationChallenges challenges;

	ParticipationCommandService(ParticipationRepository participations, ApplicationChallenges challenges) {
		this.participations = participations;
		this.challenges = challenges;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public long registerHost(long challengeId, long userId, Instant now, int pointAmount) {
		return participations.save(Participation.host(challengeId, userId, now, pointAmount)).getId();
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<List<InvalidatedApplication>> invalidatePendingWithoutPoints(long userId) {
		List<Participation> pending = participations.findPendingByUserIdForUpdate(userId);
		if (pending.stream().anyMatch(Participation::hasPoints)) {
			return Optional.empty();
		}
		Map<Long, Long> hostUserIds = participations
				.findHosts(pending.stream().map(Participation::getChallengeId).distinct().toList()).stream()
				.collect(Collectors.toMap(Participation::getChallengeId, Participation::getUserId));
		pending.forEach(Participation::invalidate);
		return Optional.of(pending.stream()
				.map(p -> new InvalidatedApplication(p.getId(), p.getChallengeId(), hostOf(hostUserIds, p)))
				.toList());
	}

	/**
	 * 락 순서(포인트 계정 → 챌린지 → 참가자)에 맞춰 챌린지를 먼저 잠근 뒤 참가자 행을 잠근다.
	 * TODO(D-24): 이탈하는 사람을 MEMBER로 내리는 것과 부방장이 없을 때의 처리(O-9)는 제안이다.
	 */
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public RoleHandOver handOverRoles(long challengeId, long departingParticipationId) {
		challenges.lock(challengeId).orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		Participation departing = participations.findByIdForUpdate(departingParticipationId)
				.filter(p -> p.getChallengeId() == challengeId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		if (departing.getRole() == ParticipationRole.MEMBER) {
			return RoleHandOver.unchanged();
		}
		// 합류 순서(joined_at, id)대로 정렬되어 있다. 떠나는 사람은 제외한다.
		List<Participation> remaining = participations.findActiveByChallengeForUpdate(challengeId).stream()
				.filter(p -> p.getId() != departingParticipationId)
				.toList();
		if (departing.getRole() == ParticipationRole.HOST) {
			return handOverHost(challengeId, departing, remaining);
		}
		Participation newDeputy = firstMember(remaining);
		departing.stepDown();
		if (newDeputy == null) {
			return RoleHandOver.unchanged();
		}
		newDeputy.assignDeputy();
		return new RoleHandOver(null, newDeputy.getUserId(), false);
	}

	private RoleHandOver handOverHost(long challengeId, Participation departing, List<Participation> remaining) {
		Participation deputy = remaining.stream()
				.filter(p -> p.getRole() == ParticipationRole.DEPUTY)
				.findFirst()
				.orElse(null);
		if (deputy == null) {
			// 부방장이 없을 때의 대체 절차는 위키 O-9(미정)라 역할을 바꾸지 않는다.
			log.warn("방장 승계 불가: 부방장이 없음 challengeId={}, departingParticipationId={}", challengeId, departing.getId());
			return new RoleHandOver(null, null, true);
		}
		deputy.succeedAsHost();
		departing.stepDown();
		challenges.changeOwner(challengeId, deputy.getUserId());
		Participation newDeputy = firstMember(remaining);
		if (newDeputy == null) {
			return new RoleHandOver(deputy.getUserId(), null, false);
		}
		newDeputy.assignDeputy();
		return new RoleHandOver(deputy.getUserId(), newDeputy.getUserId(), false);
	}

	/** 남은 참여자 중 합류 순서가 가장 빠른 일반 참가자. 없으면 null. */
	private static Participation firstMember(List<Participation> remaining) {
		return remaining.stream().filter(p -> p.getRole() == ParticipationRole.MEMBER).findFirst().orElse(null);
	}

	private static long hostOf(Map<Long, Long> hostUserIds, Participation application) {
		Long hostUserId = hostUserIds.get(application.getChallengeId());
		if (hostUserId == null) {
			throw new IllegalStateException("방장이 없는 챌린지에 대기 신청이 있습니다: challengeId=" + application.getChallengeId());
		}
		return hostUserId;
	}
}
