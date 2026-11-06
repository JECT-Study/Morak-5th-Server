package com.morak.feature.user.application.query;

import java.util.Collection;
import java.util.Map;

/** 다른 모듈(채팅 등)에 공개하는 사용자 조회 API. */
public interface UserQuery {

	/** userId -> 표시용 프로필. 존재하지 않는 id는 결과에 없다. */
	Map<Long, UserProfile> findProfilesByIds(Collection<Long> userIds);

	record UserProfile(Long userId, String nickname, String profileImageUrl) {}
}
