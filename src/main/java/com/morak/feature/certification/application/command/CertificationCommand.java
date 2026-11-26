package com.morak.feature.certification.application.command;

import java.time.LocalDate;

/** 다른 모듈(챌린지 일일 배치 등)에 공개하는 인증 변경 API. */
public interface CertificationCommand {

	/**
	 * 어제(KST)까지 인증 의무가 있었는데 제출이 없는 날짜에 FAILED 행을 만든다(위키 §4.4, §4.9 ①). 여러 번 돌려도 결과가 같다.
	 *
	 * @param today 배치 기준일(KST). 오늘 인증은 아직 마감 전이라 만들지 않는다
	 * @return 새로 만든 FAILED 행 수
	 */
	int createMissedFailures(LocalDate today);
}
