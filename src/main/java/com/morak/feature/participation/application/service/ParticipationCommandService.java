package com.morak.feature.participation.application.service;

import com.morak.feature.participation.application.command.ParticipationCommand;
import com.morak.feature.participation.domain.entity.Participation;
import com.morak.feature.participation.domain.repository.ParticipationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class ParticipationCommandService implements ParticipationCommand {

	private final ParticipationRepository participations;

	ParticipationCommandService(ParticipationRepository participations) {
		this.participations = participations;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void registerHost(long challengeId, long userId, Instant now) {
		participations.save(Participation.host(challengeId, userId, now));
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

	private static long hostOf(Map<Long, Long> hostUserIds, Participation application) {
		Long hostUserId = hostUserIds.get(application.getChallengeId());
		if (hostUserId == null) {
			throw new IllegalStateException("방장이 없는 챌린지에 대기 신청이 있습니다: challengeId=" + application.getChallengeId());
		}
		return hostUserId;
	}
}
