package com.morak.feature.certification.application.service;

import com.morak.feature.certification.application.command.CertificationCommand;
import com.morak.feature.certification.application.port.out.CertificationFailureStore;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 00:00 미제출 FAILED 생성(위키 §4.9 ①). 챌린지 일일 배치가 시작 처리·자동 폐기보다 먼저 부른다.
 *
 * <p>TODO(D-43): 서버가 멈춰 놓친 날짜는 매일 시작일부터 어제까지 다시 채워 보정한다. 대상은 지금 ACTIVE인 참가자뿐이라, 놓친 사이에 퇴장한
 * 사람의 퇴장 전 미제출은 보정하지 않는다. TODO(O-23): 면제일은 정의가 없어 제외하지 않는다.
 */
@Service
public class CreateMissedCertificationFailuresService implements CertificationCommand {

	private static final Logger log = LoggerFactory.getLogger(CreateMissedCertificationFailuresService.class);

	private final CertificationFailureStore store;
	private final Clock clock;

	public CreateMissedCertificationFailuresService(CertificationFailureStore store, Clock clock) {
		this.store = store;
		this.clock = clock;
	}

	@Override
	public int createMissedFailures(LocalDate today) {
		int created = store.insertMissing(today.minusDays(1), clock.instant());
		log.info("미제출 인증 FAILED 생성: today={}, created={}", today, created);
		return created;
	}
}
