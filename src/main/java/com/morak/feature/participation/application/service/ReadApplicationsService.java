package com.morak.feature.participation.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.participation.application.dto.query.ApplicationListStatus;
import com.morak.feature.participation.application.dto.result.ApplicationView;
import com.morak.feature.participation.application.dto.result.ApplicationView.Applicant;
import com.morak.feature.participation.application.port.out.ApplicantProfileReader;
import com.morak.feature.participation.application.port.out.ApplicantProfileReader.ApplicantSource;
import com.morak.feature.participation.application.port.out.ApplicationChallenges;
import com.morak.feature.participation.application.port.out.ApplicationChallenges.ChallengeInfo;
import com.morak.feature.participation.application.query.ParticipationQuery;
import com.morak.feature.participation.application.query.ParticipationQuery.Application;
import com.morak.feature.participation.application.query.ParticipationQuery.ParticipationRecord;
import com.morak.feature.participation.domain.exception.ParticipationErrorCode;
import com.morak.feature.participation.domain.policy.CertificationRate;
import com.morak.feature.participation.domain.policy.MorakiStage;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방장이 보는 참여 신청 목록(위키 §4.3, §4.1). 승인 대기 목록은 방장 본인 전용이다.
 * 신청자마다 각오 한마디와 프로필(개인 인증률·모락이 단계·메달·완주·중도 실패 횟수)을 함께 돌려준다.
 */
@Service
public class ReadApplicationsService {

	private static final ParticipationRecord NO_RECORD = new ParticipationRecord(0, 0);

	private final ApplicationChallenges challenges;
	private final ParticipationQuery participationQuery;
	private final ApplicantProfileReader profiles;

	public ReadApplicationsService(
			ApplicationChallenges challenges, ParticipationQuery participationQuery, ApplicantProfileReader profiles) {
		this.challenges = challenges;
		this.participationQuery = participationQuery;
		this.profiles = profiles;
	}

	@Transactional(readOnly = true)
	public List<ApplicationView> list(long hostUserId, long challengeId, ApplicationListStatus status) {
		ChallengeInfo challenge = challenges.find(challengeId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		if (challenge.ownerUserId() != hostUserId) {
			throw new BusinessException(ParticipationErrorCode.NOT_CHALLENGE_HOST);
		}
		List<Application> applications = switch (status) {
			case PENDING -> participationQuery.findPendingApplications(challengeId);
			case ACTIVE -> participationQuery.findDecidedApplications(challengeId, "ACTIVE");
			case REJECTED -> participationQuery.findDecidedApplications(challengeId, "REJECTED");
		};
		if (applications.isEmpty()) {
			return List.of();
		}
		Set<Long> userIds = applications.stream().map(Application::userId).collect(Collectors.toSet());
		Map<Long, ApplicantSource> sources = profiles.findByUserIds(userIds);
		Map<Long, ParticipationRecord> records = participationQuery.findRecordsByUserIds(userIds);
		return applications.stream()
				.map(application -> toView(application, status, sources.get(application.userId()),
						records.getOrDefault(application.userId(), NO_RECORD)))
				.toList();
	}

	private static ApplicationView toView(
			Application application, ApplicationListStatus status, ApplicantSource source, ParticipationRecord record) {
		return new ApplicationView(
				application.participationId(),
				application.invalidated() ? "INVALIDATED" : status.name(),
				application.applyMessage(),
				application.appliedAt(),
				toApplicant(application.userId(), source, record));
	}

	private static Applicant toApplicant(long userId, ApplicantSource source, ParticipationRecord record) {
		if (source == null) {
			return new Applicant(userId, null, null, null, null, record.completedCount(), record.expelledCount(), List.of());
		}
		OptionalInt percent = CertificationRate.percent(source.approvedCount(), source.obligationCount());
		Integer rate = percent.isPresent() ? percent.getAsInt() : null;
		Integer stage = MorakiStage.of(source.approvedCount(), source.obligationCount())
				.map(MorakiStage::level).orElse(null);
		List<ApplicationView.Medal> medals = source.medals().stream()
				.map(medal -> new ApplicationView.Medal(medal.code(), medal.name(), medal.iconUrl()))
				.toList();
		return new Applicant(
				userId,
				source.nickname(),
				source.profileImageUrl(),
				rate,
				stage,
				record.completedCount(),
				record.expelledCount(),
				medals);
	}
}
