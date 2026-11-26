package com.morak.feature.certification.domain.vo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 위키 §4.4 판정 담당 표. */
class CertificationJudgeTest {

	@ParameterizedTest(name = "{0}가 {1}의 인증 판정 = {2}")
	@CsvSource({
		// 방장: 자신 외 모든 팀원(부방장 포함)
		"HOST, MEMBER, true",
		"HOST, DEPUTY, true",
		"HOST, HOST, false",
		// 부방장: 방장 인증만
		"DEPUTY, HOST, true",
		"DEPUTY, MEMBER, false",
		"DEPUTY, DEPUTY, false",
		// 일반 참여자는 판정하지 않는다
		"MEMBER, MEMBER, false",
		"MEMBER, HOST, false",
	})
	void judgesByRole(String judgeRole, String submitterRole, boolean expected) {
		assertEquals(expected, CertificationJudge.canJudge(judgeRole, submitterRole));
	}

	@ParameterizedTest
	@CsvSource({"HOST, DEPUTY", "DEPUTY, HOST", "MEMBER, HOST"})
	void judgeOfSubmitter(String submitterRole, CertificationJudge expected) {
		assertEquals(expected, CertificationJudge.forSubmitter(submitterRole));
	}
}
