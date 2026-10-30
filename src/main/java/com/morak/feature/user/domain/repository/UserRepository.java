package com.morak.feature.user.domain.repository;

import com.morak.feature.user.domain.entity.User;
import java.util.Optional;

public interface UserRepository {

	Optional<User> findById(Long id);

	User save(User user);
}
