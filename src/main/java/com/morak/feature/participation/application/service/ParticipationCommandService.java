package com.morak.feature.participation.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.participation.application.command.ParticipationCommand;
import com.morak.feature.participation.application.port.out.ApplicantPoints;
import com.morak.feature.participation.application.port.out.ApplicationChallenges;
import com.morak.feature.participation.domain.entity.Participation;
import com.morak.feature.participation.domain.entity.ParticipationResult;
import com.morak.feature.participation.domain.enums.ParticipationRole;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import com.morak.feature.participation.domain.repository.ParticipationRepository;
import com.morak.feature.participation.domain.repository.ParticipationResultRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
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
	private final ParticipationResultRepository results;
	private final ApplicationChallenges challenges;
	private final ApplicantPoints points;

	ParticipationCommandService(ParticipationRepository participations, ParticipationResultRepository results,
			ApplicationChallenges challenges, ApplicantPoints points) {
		this.participations = participations;
		this.results = results;
		this.challenges = challenges;
		this.points = points;
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

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Set<Long> lockPointAccountsForDiscard(long challengeId) {
		Set<Long> userIds = new TreeSet<>();
		for (Participation participation : participations.findDiscardableByChallenge(challengeId)) {
			if (participation.isPointHeld()) {
				userIds.add(participation.getUserId());
			}
		}
		points.lockAccounts(userIds);
		return userIds;
	}

	/** TODO(D-26): 폐기 중 새 지원이 끼어든 경우 409로 되돌리는 방식은 D-24와 같은 제안이다. */
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public List<DiscardedParticipation> discardAll(
			long challengeId, String challengeTitle, Set<Long> lockedUserIds, Instant now) {
		List<Participation> targets = participations.findDiscardableByChallengeForUpdate(challengeId);
		for (Participation target : targets) {
			if (target.isPointHeld() && !lockedUserIds.contains(target.getUserId())) {
				throw new BusinessException(ParticipationErrorCode.PENDING_APPLICATIONS_CHANGED);
			}
		}
		return targets.stream().map(target -> discard(target, challengeTitle, now)).toList();
	}

	private DiscardedParticipation discard(Participation target, String challengeTitle, Instant now) {
		boolean pointHeld = target.isPointHeld();
		int refunded = pointHeld ? target.getPointAmount() : 0;
		target.discard(now);
		if (pointHeld) {
			points.release(target.getUserId(), target.getId(), challengeTitle, refunded);
		}
		return new DiscardedParticipation(
				target.getId(), target.getUserId(), target.getRole() == ParticipationRole.HOST, refunded);
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

	/** TODO(D-28): 반환 포인트 컬럼 없이 저장한다(반환은 원장 SETTLE에 남는다). 연속 인증 일수는 호출한 쪽 값을 그대로 쓴다. */
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public int recordResults(List<ResultInput> inputs, Instant finalizedAt) {
		if (inputs.isEmpty()) {
			return 0;
		}
		List<Long> ids = inputs.stream().map(ResultInput::participationId).toList();
		Map<Long, Participation> byId = participations.findAllByIds(ids).stream()
				.collect(Collectors.toMap(Participation::getId, Function.identity()));
		// 하나라도 없는 ID면 저장을 시작하기 전에 거절한다.
		if (ids.stream().anyMatch(id -> !byId.containsKey(id))) {
			throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
		}
		// 이미 저장된 참가자와 이번 입력에서 먼저 처리한 참가자를 함께 담아 중복 저장을 막는다.
		Set<Long> handled = new HashSet<>(results.findExistingParticipationIds(ids));
		int saved = 0;
		for (ResultInput input : inputs) {
			Participation participation = byId.get(input.participationId());
			if (!participation.canHaveResult()) {
				log.info("결과 저장 건너뜀: participationId={}, status={}", participation.getId(), participation.getStatus());
				continue;
			}
			if (!handled.add(participation.getId())) {
				continue;
			}
			results.save(ParticipationResult.of(participation.getId(), participation.getChallengeId(),
					participation.getUserId(), input.approvedCount(), input.totalCount(), input.currentStreak(),
					input.maxStreak(), input.completed(), finalizedAt));
			saved++;
		}
		return saved;
	}

	private static long hostOf(Map<Long, Long> hostUserIds, Participation application) {
		Long hostUserId = hostUserIds.get(application.getChallengeId());
		if (hostUserId == null) {
			throw new IllegalStateException("방장이 없는 챌린지에 대기 신청이 있습니다: challengeId=" + application.getChallengeId());
		}
		return hostUserId;
	}
}
