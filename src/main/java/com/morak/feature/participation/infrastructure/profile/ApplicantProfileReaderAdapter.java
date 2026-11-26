package com.morak.feature.participation.infrastructure.profile;

import com.morak.feature.certification.application.query.CertificationQuery;
import com.morak.feature.certification.application.query.CertificationQuery.CertificationStats;
import com.morak.feature.medal.application.query.MedalQuery;
import com.morak.feature.medal.application.query.MedalQuery.MedalInfo;
import com.morak.feature.participation.application.port.out.ApplicantProfileReader;
import com.morak.feature.user.application.query.UserQuery;
import com.morak.feature.user.application.query.UserQuery.UserProfile;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 신청자 프로필 포트를 사용자·인증·메달 모듈의 공개 조회 API로 연결한다. */
@Component
class ApplicantProfileReaderAdapter implements ApplicantProfileReader {

	private static final CertificationStats NO_CERTIFICATIONS = new CertificationStats(0, 0);

	private final UserQuery userQuery;
	private final CertificationQuery certificationQuery;
	private final MedalQuery medalQuery;

	ApplicantProfileReaderAdapter(UserQuery userQuery, CertificationQuery certificationQuery, MedalQuery medalQuery) {
		this.userQuery = userQuery;
		this.certificationQuery = certificationQuery;
		this.medalQuery = medalQuery;
	}

	@Override
	public Map<Long, ApplicantSource> findByUserIds(Collection<Long> userIds) {
		Map<Long, UserProfile> profiles = userQuery.findProfilesByIds(userIds);
		Map<Long, CertificationStats> stats = certificationQuery.findStatsByUserIds(userIds);
		Map<Long, List<MedalInfo>> medals = medalQuery.findEarnedByUserIds(userIds);

		Map<Long, ApplicantSource> result = new HashMap<>();
		profiles.forEach((userId, profile) -> {
			CertificationStats stat = stats.getOrDefault(userId, NO_CERTIFICATIONS);
			List<Medal> earned = medals.getOrDefault(userId, List.of()).stream()
					.map(medal -> new Medal(medal.code(), medal.name(), medal.iconUrl()))
					.toList();
			result.put(userId, new ApplicantSource(
					profile.nickname(), profile.profileImageUrl(), stat.approvedCount(), stat.obligationCount(), earned));
		});
		return result;
	}
}
