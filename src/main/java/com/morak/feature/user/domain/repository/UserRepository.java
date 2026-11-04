package com.morak.feature.user.domain.repository;

import com.morak.feature.user.domain.vo.Nickname;
import com.morak.feature.user.domain.vo.UserId;
import com.morak.feature.user.domain.entity.User;
import java.util.Optional;

public interface UserRepository {

	Optional<User> findById(UserId id);

	Optional<User> findByIdForUpdate(UserId id);

	boolean existsByNickname(Nickname nickname);

	User save(User user);

	void flushOnboarding();
}
