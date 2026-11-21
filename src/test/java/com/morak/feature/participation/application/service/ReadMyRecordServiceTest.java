package com.morak.feature.participation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.participation.application.dto.result.MyRecordView;
import com.morak.feature.participation.application.port.out.ApplicantProfileReader;
import com.morak.feature.participation.application.port.out.ApplicantProfileReader.ApplicantSource;
import com.morak.feature.participation.application.query.ParticipationQuery;
import com.morak.feature.participation.application.query.ParticipationQuery.ParticipationRecord;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 위키 §7 개인 인증률·모락이 케이스: 누적 합계로 계산하고, 인증 기록이 없으면 Stage 1이 아니라 단계 없음이다. */
class ReadMyRecordServiceTest {

	private static final long ME = 7L;

	private final ParticipationQuery participationQuery = mock(ParticipationQuery.class);
	private final ApplicantProfileReader profiles = mock(ApplicantProfileReader.class);
	private final ReadMyRecordService service = new ReadMyRecordService(participationQuery, profiles);

	@Test
	void returnsRateStageAndParticipationCounts() {
		givenCertifications(20, 25);
		when(participationQuery.findRecordsByUserIds(Set.of(ME))).thenReturn(Map.of(ME, new ParticipationRecord(3, 1)));

		MyRecordView record = service.read(ME);

		assertEquals(80, record.certificationRate());
		assertEquals(3, record.morakiStage());
		assertTrue(record.hasCertificationRecord());
		assertEquals(3, record.completedCount());
		assertEquals(1, record.midwayFailCount());
	}

	@Test
	void noCertificationRecordHasNoRateAndNoStage() {
		givenCertifications(0, 0);
		when(participationQuery.findRecordsByUserIds(Set.of(ME))).thenReturn(Map.of());

		MyRecordView record = service.read(ME);

		assertNull(record.certificationRate());
		assertNull(record.morakiStage());
		assertFalse(record.hasCertificationRecord());
		assertEquals(0, record.completedCount());
		assertEquals(0, record.midwayFailCount());
	}

	@Test
	void stageBoundariesBelongToTheHigherStage() {
		assertEquals(2, stageOf(30, 100));
		assertEquals(3, stageOf(60, 100));
		assertEquals(4, stageOf(85, 100));
		assertEquals(5, stageOf(95, 100));
		assertEquals(1, stageOf(29, 100));
	}

	@Test
	void unknownUserIsNotFound() {
		when(profiles.findByUserIds(Set.of(ME))).thenReturn(Map.of());

		BusinessException exception = assertThrows(BusinessException.class, () -> service.read(ME));
		assertEquals(CommonErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
	}

	private int stageOf(int approved, int obligation) {
		givenCertifications(approved, obligation);
		when(participationQuery.findRecordsByUserIds(Set.of(ME))).thenReturn(Map.of());
		return service.read(ME).morakiStage();
	}

	private void givenCertifications(int approved, int obligation) {
		when(profiles.findByUserIds(Set.of(ME)))
				.thenReturn(Map.of(ME, new ApplicantSource("러너", null, approved, obligation, List.of())));
	}
}
