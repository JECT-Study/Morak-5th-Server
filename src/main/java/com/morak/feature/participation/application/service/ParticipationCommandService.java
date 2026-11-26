package com.morak.feature.participation.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.participation.application.command.ParticipationCommand;
import com.morak.feature.participation.application.port.out.ApplicantPoints;
import com.morak.feature.participation.application.port.out.ApplicationChallenges;
import com.morak.feature.participation.application.port.out.ApplicationNotifier;
import com.morak.feature.participation.domain.entity.Participation;
import com.morak.feature.participation.domain.entity.ParticipationResult;
import com.morak.feature.participation.domain.enums.ParticipationRole;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import com.morak.feature.participation.domain.repository.ParticipationRepository;
import com.morak.feature.participation.domain.repository.ParticipationResultRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
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
	/** 공개 챌린지는 2명만 남으면 조기 종료한다(위키 §4.4). */
	static final int EARLY_END_MAX_REMAINING = 2;

	private final ParticipationRepository participations;
	private final ParticipationResultRepository results;
	private final ApplicationChallenges challenges;
	private final ApplicantPoints points;
	private final ApplicationNotifier notifier;

	ParticipationCommandService(ParticipationRepository participations, ParticipationResultRepository results,
			ApplicationChallenges challenges, ApplicantPoints points, ApplicationNotifier notifier) {
		this.participations = participations;
		this.results = results;
		this.challenges = challenges;
		this.points = points;
		this.notifier = notifier;
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
		return lockPointAccounts(participations.findDiscardableByChallenge(challengeId));
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Set<Long> lockPendingPointAccounts(long challengeId) {
		return lockPointAccounts(participations.findPendingByChallenge(challengeId));
	}

	private Set<Long> lockPointAccounts(List<Participation> targets) {
		Set<Long> userIds = new TreeSet<>();
		for (Participation target : targets) {
			if (target.isPointHeld()) {
				userIds.add(target.getUserId());
			}
		}
		points.lockAccounts(userIds);
		return userIds;
	}

	/**
	 * 정원이 차는 승인의 자동 거절(위키 §4.3)과 같은 방식으로 거절·반환·알림 #2를 처리한다.
	 * TODO(D-29): 시작 중 새 지원이 끼어든 경우 409로 되돌리는 방식과 알림 #2 문구 재사용은 제안이다.
	 */
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public List<RejectedApplication> rejectPendingOnStart(long challengeId, String challengeTitle, Set<Long> lockedUserIds) {
		List<Participation> pending = participations.findPendingByChallengeForUpdate(challengeId);
		requireLockedPointAccounts(pending, lockedUserIds);
		return pending.stream().map(application -> rejectOnStart(application, challengeId, challengeTitle)).toList();
	}

	private RejectedApplication rejectOnStart(Participation application, long challengeId, String challengeTitle) {
		boolean pointHeld = application.isPointHeld();
		int refunded = pointHeld ? application.getPointAmount() : 0;
		application.reject();
		if (pointHeld) {
			points.release(application.getUserId(), application.getId(), challengeTitle, refunded);
		}
		notifier.rejected(application.getUserId(), challengeId, application.getId(), challengeTitle, refunded);
		return new RejectedApplication(application.getId(), application.getUserId(), refunded);
	}

	/** TODO(D-26): 폐기 중 새 지원이 끼어든 경우 409로 되돌리는 방식은 D-24와 같은 제안이다. */
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public List<DiscardedParticipation> discardAll(
			long challengeId, String challengeTitle, Set<Long> lockedUserIds, Instant now) {
		List<Participation> targets = participations.findDiscardableByChallengeForUpdate(challengeId);
		requireLockedPointAccounts(targets, lockedUserIds);
		return targets.stream().map(target -> discard(target, challengeTitle, now)).toList();
	}

	/** 상태나 포인트를 바꾸기 전에 모든 대상의 계정 잠금을 확인한다. */
	private static void requireLockedPointAccounts(List<Participation> targets, Set<Long> lockedUserIds) {
		for (Participation target : targets) {
			if (target.isPointHeld() && !lockedUserIds.contains(target.getUserId())) {
				throw new BusinessException(ParticipationErrorCode.PENDING_APPLICATIONS_CHANGED);
			}
		}
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

	/**
	 * 한 명씩 승계한 뒤 퇴장시켜, 같은 날 방장·부방장이 함께 퇴장해도 다음 사람의 승계가 앞사람의 결과를 본다.
	 * 승계·남은 인원 조회는 JPQL이라 앞서 바꾼 상태가 먼저 반영된다(flush). TODO(D-45): 공개 챌린지가 한 번에 1명 이하로
	 * 줄어도 조기 종료로 처리하는 것과, 비공개에서 1명만 남는 경우의 폐지(D-37 ②)를 아직 하지 않는 것은 제안이다.
	 */
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public ExpelOutcome expel(long challengeId, Collection<Long> participationIds, LocalDate today, Instant now) {
		ApplicationChallenges.ChallengeInfo challenge = challenges.lock(challengeId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		Set<Long> targets = new HashSet<>(participationIds);
		List<ExpelledMember> expelled = new ArrayList<>();
		for (Participation participation : participations.findActiveByChallengeForUpdate(challengeId)) {
			if (!targets.contains(participation.getId())) {
				continue;
			}
			handOverRoles(challengeId, participation.getId());
			participation.expel(now);
			expelled.add(new ExpelledMember(participation.getId(), participation.getUserId()));
		}
		if (expelled.size() < targets.size()) {
			log.info("퇴장 대상 일부 건너뜀(ACTIVE 아님): challengeId={}, targets={}, expelled={}",
					challengeId, targets, expelled);
		}
		if (expelled.isEmpty() || challenge.isPrivate()) {
			return new ExpelOutcome(expelled, List.of());
		}
		List<Participation> remaining = participations.findActiveByChallengeForUpdate(challengeId);
		if (remaining.size() > EARLY_END_MAX_REMAINING) {
			return new ExpelOutcome(expelled, List.of());
		}
		remaining.forEach(p -> p.endEarly(now));
		challenges.endEarly(challengeId, today);
		log.info("공개 챌린지 조기 종료: challengeId={}, remaining={}", challengeId, remaining.size());
		return new ExpelOutcome(expelled, remaining.stream().map(Participation::getUserId).toList());
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
