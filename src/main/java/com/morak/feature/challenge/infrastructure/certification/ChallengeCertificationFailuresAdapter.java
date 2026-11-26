package com.morak.feature.challenge.infrastructure.certification;

import com.morak.feature.certification.application.command.CertificationCommand;
import com.morak.feature.challenge.application.port.out.ChallengeCertificationFailures;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** 챌린지 포트를 인증 모듈의 공개 변경 API로 연결한다. */
@Component
class ChallengeCertificationFailuresAdapter implements ChallengeCertificationFailures {

	private final CertificationCommand certificationCommand;

	ChallengeCertificationFailuresAdapter(CertificationCommand certificationCommand) {
		this.certificationCommand = certificationCommand;
	}

	@Override
	public void createMissed(LocalDate today) {
		certificationCommand.createMissedFailures(today);
	}
}
