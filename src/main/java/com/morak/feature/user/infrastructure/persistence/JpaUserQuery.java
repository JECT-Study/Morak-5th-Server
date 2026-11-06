package com.morak.feature.user.infrastructure.persistence;

import com.morak.feature.user.application.query.UserQuery;
import com.morak.feature.user.domain.entity.User;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaUserQuery implements UserQuery {

	private final JpaUserRepository jpaRepository;

	public JpaUserQuery(JpaUserRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, UserProfile> findProfilesByIds(Collection<Long> userIds) {
		Map<Long, UserProfile> result = new HashMap<>();
		if (userIds.isEmpty()) {
			return result;
		}
		for (User user : jpaRepository.findAllById(userIds)) {
			result.put(user.getId(), new UserProfile(user.getId(), user.getNickname(), user.getProfileImageUrl()));
		}
		return result;
	}
}
