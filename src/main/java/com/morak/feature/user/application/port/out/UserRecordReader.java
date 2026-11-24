package com.morak.feature.user.application.port.out;

import java.util.List;

/** 프로필 통계(위키 §4.1). 다른 도메인(participation·medal)은 이 포트로만 조회한다. */
public interface UserRecordReader {

	UserRecord read(long userId);

	/**
	 * @param certificationRate 개인 인증률(정수 %). 인증 기록이 없으면 null
	 * @param morakiStage 모락이 단계(1~5). 인증 기록이 없으면 null
	 * @param hasCertificationRecord false면 "인증 기록 없음"을 보여 준다
	 * @param completedCount 누적 완주 횟수
	 * @param midwayFailCount 중도 실패(퇴장) 챌린지 횟수
	 * @param medals 획득 메달(획득 순)
	 */
	record UserRecord(
			Integer certificationRate,
			Integer morakiStage,
			boolean hasCertificationRecord,
			int completedCount,
			int midwayFailCount,
			List<Medal> medals) {}

	record Medal(String code, String name, String iconUrl) {}
}
