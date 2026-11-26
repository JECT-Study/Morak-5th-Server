package com.morak.feature.certification.application.port.out;

import java.time.Instant;
import java.time.LocalDate;

/** 미제출 인증의 FAILED 행을 저장한다. */
public interface CertificationFailureStore {

	/**
	 * 진행 중·정산 대기 챌린지의 ACTIVE 참가자마다 시작일부터 {@code lastDate}(종료일을 넘지 않음)까지 인증이 없는 날짜에 FAILED 행을 만든다.
	 * 이미 행이 있는 날짜는 건드리지 않는다.
	 *
	 * @return 새로 만든 행 수
	 */
	int insertMissing(LocalDate lastDate, Instant now);
}
