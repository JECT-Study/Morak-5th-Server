package com.morak.feature.challenge.infrastructure.certification;

import com.morak.feature.challenge.application.port.out.ChallengeCertificationReader;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 인증 집계 연동 전 임시 구현. 항상 빈 결과를 돌려주므로 응답의 인증 관련 필드는 null이다.
 *
 * <p>TODO(D-23): certification.submitted_at이 NOT NULL이라 00:00 배치의 FAILED 행(제출 시각 NULL, 위키 §4.4)을 저장할 수 없고 면제일
 * 표현도 없어서 실패·의무 횟수를 셀 수 없다. 인증 스키마가 정리되면 CertificationQuery 집계로 교체한다.
 */
@Component
class NullChallengeCertificationReader implements ChallengeCertificationReader {

	@Override
	public Map<Long, CertificationStats> findStats(Collection<Long> participationIds, LocalDate today) {
		return Map.of();
	}
}
