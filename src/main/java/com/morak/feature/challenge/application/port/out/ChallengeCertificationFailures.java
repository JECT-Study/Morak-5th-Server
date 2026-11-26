package com.morak.feature.challenge.application.port.out;

import java.time.LocalDate;

/** 챌린지 일일 배치가 certification 도메인에 맡기는 미제출 FAILED 생성(위키 §4.9 ①). */
public interface ChallengeCertificationFailures {

	/** @param today 배치 기준일(KST). 어제까지의 미제출을 FAILED로 만든다 */
	void createMissed(LocalDate today);
}
