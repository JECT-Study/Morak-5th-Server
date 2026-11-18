package com.morak.feature.participation.domain.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** 위키 §7 "개인 인증률·모락이" 케이스. */
class MorakiStageTest {

	@Test
	void obligation25AndApproved20Is80PercentStage3() {
		assertEquals(OptionalInt.of(80), CertificationRate.percent(20, 25));
		assertEquals(Optional.of(MorakiStage.STAGE_3), MorakiStage.of(20, 25));
	}

	@Test
	void boundaryValuesBelongToTheHigherStage() {
		assertEquals(Optional.of(MorakiStage.STAGE_2), MorakiStage.of(30, 100));
		assertEquals(Optional.of(MorakiStage.STAGE_3), MorakiStage.of(60, 100));
		assertEquals(Optional.of(MorakiStage.STAGE_4), MorakiStage.of(85, 100));
		assertEquals(Optional.of(MorakiStage.STAGE_5), MorakiStage.of(95, 100));
	}

	@Test
	void justBelowBoundaryStaysInTheLowerStage() {
		assertEquals(Optional.of(MorakiStage.STAGE_1), MorakiStage.of(29, 100));
		assertEquals(Optional.of(MorakiStage.STAGE_2), MorakiStage.of(59, 100));
		assertEquals(Optional.of(MorakiStage.STAGE_3), MorakiStage.of(84, 100));
		assertEquals(Optional.of(MorakiStage.STAGE_4), MorakiStage.of(94, 100));
	}

	@Test
	void stageUsesTheUnroundedRate() {
		// 94.6%는 표시 비율로는 95%지만 단계는 아직 Stage 4다.
		assertEquals(OptionalInt.of(95), CertificationRate.percent(946, 1000));
		assertEquals(Optional.of(MorakiStage.STAGE_4), MorakiStage.of(946, 1000));
	}

	@Test
	void noCertificationRecordHasNeitherRateNorStage() {
		assertTrue(CertificationRate.percent(0, 0).isEmpty());
		assertTrue(MorakiStage.of(0, 0).isEmpty());
	}

	@Test
	void zeroApprovedWithObligationIsStage1() {
		assertEquals(OptionalInt.of(0), CertificationRate.percent(0, 10));
		assertEquals(Optional.of(MorakiStage.STAGE_1), MorakiStage.of(0, 10));
		assertEquals(1, MorakiStage.STAGE_1.level());
	}

	@Test
	void percentRoundsHalfUpAtTheFirstDecimal() {
		// 1/8 = 12.5% → 13%, 1/3 = 33.3% → 33%, 2/3 = 66.7% → 67%
		assertEquals(OptionalInt.of(13), CertificationRate.percent(1, 8));
		assertEquals(OptionalInt.of(33), CertificationRate.percent(1, 3));
		assertEquals(OptionalInt.of(67), CertificationRate.percent(2, 3));
		assertEquals(OptionalInt.of(100), CertificationRate.percent(7, 7));
	}
}
