package com.morak.feature.user.infrastructure.participation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.morak.feature.medal.application.query.MedalQuery;
import com.morak.feature.medal.application.query.MedalQuery.MedalInfo;
import com.morak.feature.participation.application.port.out.ApplicantProfileReader;
import com.morak.feature.participation.application.port.out.ApplicantProfileReader.ApplicantSource;
import com.morak.feature.participation.application.query.ParticipationQuery;
import com.morak.feature.participation.application.query.ParticipationQuery.ParticipationRecord;
import com.morak.feature.participation.application.service.ReadMyRecordService;
import com.morak.feature.user.application.port.out.UserRecordReader.Medal;
import com.morak.feature.user.application.port.out.UserRecordReader.UserRecord;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 위키 §7 개인 인증률·모락이 케이스를 타인 프로필 경로(실제 ReadMyRecordService 계산)로 확인한다. */
class UserRecordReaderAdapterTest {

	private static final long USER = 7L;

	private final ParticipationQuery participationQuery = mock(ParticipationQuery.class);
	private final ApplicantProfileReader profiles = mock(ApplicantProfileReader.class);
	private final MedalQuery medalQuery = mock(MedalQuery.class);
	private final UserRecordReaderAdapter adapter = new UserRecordReaderAdapter(
			new ReadMyRecordService(participationQuery, profiles), medalQuery);

	@Test
	void returnsRateStageCountsAndMedals() {
		givenCertifications(20, 25);
		when(participationQuery.findRecordsByUserIds(Set.of(USER)))
				.thenReturn(Map.of(USER, new ParticipationRecord(3, 1)));
		when(medalQuery.findEarnedByUserIds(Set.of(USER)))
				.thenReturn(Map.of(USER, List.of(new MedalInfo("COMPLETE_1", "메달 1-1", "https://example.com/m.png"))));

		UserRecord record = adapter.read(USER);

		assertEquals(80, record.certificationRate());
		assertEquals(3, record.morakiStage());
		assertTrue(record.hasCertificationRecord());
		assertEquals(3, record.completedCount());
		assertEquals(1, record.midwayFailCount());
		assertEquals(List.of(new Medal("COMPLETE_1", "메달 1-1", "https://example.com/m.png")), record.medals());
	}

	@Test
	void stageBoundariesBelongToTheHigherStage() {
		assertEquals(2, read(30, 100).morakiStage());
		assertEquals(3, read(60, 100).morakiStage());
		assertEquals(4, read(85, 100).morakiStage());
		assertEquals(5, read(95, 100).morakiStage());
		assertEquals(1, read(29, 100).morakiStage());
	}

	@Test
	void noCertificationRecordHasNoRateNoStageAndNoMedals() {
		UserRecord record = read(0, 0);

		assertNull(record.certificationRate());
		assertNull(record.morakiStage());
		assertFalse(record.hasCertificationRecord());
		assertEquals(List.of(), record.medals());
	}

	private UserRecord read(int approved, int obligation) {
		givenCertifications(approved, obligation);
		when(participationQuery.findRecordsByUserIds(Set.of(USER))).thenReturn(Map.of());
		when(medalQuery.findEarnedByUserIds(Set.of(USER))).thenReturn(Map.of());
		return adapter.read(USER);
	}

	private void givenCertifications(int approved, int obligation) {
		when(profiles.findByUserIds(Set.of(USER)))
				.thenReturn(Map.of(USER, new ApplicantSource("러너", null, approved, obligation, List.of())));
	}
}
