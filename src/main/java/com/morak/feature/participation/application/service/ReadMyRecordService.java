package com.morak.feature.participation.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.participation.application.dto.result.MyRecordView;
import com.morak.feature.participation.application.port.out.ApplicantProfileReader;
import com.morak.feature.participation.application.port.out.ApplicantProfileReader.ApplicantSource;
import com.morak.feature.participation.application.query.ParticipationQuery;
import com.morak.feature.participation.application.query.ParticipationQuery.ParticipationRecord;
import com.morak.feature.participation.domain.policy.CertificationRate;
import com.morak.feature.participation.domain.policy.MorakiStage;
import java.util.OptionalInt;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 기록(프로필 통계, 위키 §4.1). 개인 인증률은 진행 중인 챌린지까지 포함해 인증을 합산하므로 participation_result가 아니라
 * 인증 집계에서 읽는다. 인증 기록이 없으면 인증률·모락이 단계는 null이다.
 */
@Service
public class ReadMyRecordService {

	private static final ParticipationRecord NO_RECORD = new ParticipationRecord(0, 0);

	private final ParticipationQuery participationQuery;
	private final ApplicantProfileReader profiles;

	public ReadMyRecordService(ParticipationQuery participationQuery, ApplicantProfileReader profiles) {
		this.participationQuery = participationQuery;
		this.profiles = profiles;
	}

	@Transactional(readOnly = true)
	public MyRecordView read(long userId) {
		ApplicantSource source = profiles.findByUserIds(Set.of(userId)).get(userId);
		if (source == null) {
			throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
		}
		ParticipationRecord record = participationQuery.findRecordsByUserIds(Set.of(userId)).getOrDefault(userId, NO_RECORD);
		OptionalInt rate = CertificationRate.percent(source.approvedCount(), source.obligationCount());
		Integer stage = MorakiStage.of(source.approvedCount(), source.obligationCount())
				.map(MorakiStage::level).orElse(null);
		return new MyRecordView(
				rate.isPresent() ? rate.getAsInt() : null,
				stage,
				rate.isPresent(),
				record.completedCount(),
				record.expelledCount());
	}
}
