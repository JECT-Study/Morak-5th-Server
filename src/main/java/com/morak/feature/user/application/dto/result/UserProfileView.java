package com.morak.feature.user.application.dto.result;

import com.morak.feature.user.application.port.out.UserRecordReader.Medal;
import com.morak.feature.user.application.port.out.UserRecordReader.UserRecord;
import com.morak.feature.user.domain.entity.User;
import java.util.List;

/**
 * 타인 프로필(위키 §4.1). 승인 대기 목록은 본인 전용이라 넣지 않는다.
 *
 * @param interests 관심 분야 코드(이름순)
 * @param certificationRate 개인 인증률(정수 %, 소수 첫째 자리 half-up). 인증 기록이 없으면 null
 * @param morakiStage 모락이 단계(1~5). 인증 기록이 없으면 null이고 기본 캐릭터를 보여 준다
 * @param hasCertificationRecord false면 "인증 기록 없음"을 보여 준다
 */
public record UserProfileView(
		long userId,
		String nickname,
		String profileImageUrl,
		String bio,
		String profileLinkUrl,
		List<String> interests,
		Integer certificationRate,
		Integer morakiStage,
		boolean hasCertificationRecord,
		int completedCount,
		int midwayFailCount,
		List<Medal> medals) {

	public static UserProfileView of(User user, UserRecord record) {
		return new UserProfileView(user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getBio(),
				user.getProfileLinkUrl(), user.interestCodes(), record.certificationRate(), record.morakiStage(),
				record.hasCertificationRecord(), record.completedCount(), record.midwayFailCount(), record.medals());
	}
}
