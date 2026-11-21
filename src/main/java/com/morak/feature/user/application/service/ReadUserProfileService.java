package com.morak.feature.user.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.user.application.dto.result.UserProfileView;
import com.morak.feature.user.application.port.out.UserRecordReader;
import com.morak.feature.user.domain.entity.User;
import com.morak.feature.user.domain.repository.UserRepository;
import com.morak.feature.user.domain.vo.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 타인 프로필 조회(위키 §4.1).
 * TODO(D-32): 탈퇴했거나 온보딩 전인 사용자를 없는 사용자와 같이 404로 숨기는 것은 PM 확인 전 제안값이다.
 */
@Service
public class ReadUserProfileService {

	private final UserRepository users;
	private final UserRecordReader records;

	public ReadUserProfileService(UserRepository users, UserRecordReader records) {
		this.users = users;
		this.records = records;
	}

	@Transactional(readOnly = true)
	public UserProfileView read(long userId) {
		User user = users.findById(new UserId(userId))
				.filter(found -> found.isActive() && found.isOnboarded())
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		return UserProfileView.of(user, records.read(userId));
	}
}
