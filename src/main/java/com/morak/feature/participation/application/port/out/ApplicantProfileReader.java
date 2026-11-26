package com.morak.feature.participation.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 신청자 프로필에 필요한, 다른 도메인이 가진 정보(닉네임·사진, 누적 인증 횟수, 메달)를 읽는다. */
public interface ApplicantProfileReader {

	/** userId -> 신청자 정보. 찾을 수 없는 사용자는 결과에 없다. */
	Map<Long, ApplicantSource> findByUserIds(Collection<Long> userIds);

	/**
	 * @param approvedCount 누적 승인 횟수
	 * @param obligationCount 판정이 끝난 누적 인증 의무 횟수. 0이면 인증 기록이 없다
	 */
	record ApplicantSource(
			String nickname, String profileImageUrl, int approvedCount, int obligationCount, List<Medal> medals) {}

	record Medal(String code, String name, String iconUrl) {}
}
