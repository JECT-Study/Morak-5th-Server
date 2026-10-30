package com.morak.feature.user.infrastructure.persistence;

import com.morak.feature.user.domain.entity.User;
import com.morak.feature.user.domain.repository.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepositoryAdapter implements UserRepository {

	private final JpaUserRepository jpaRepository;

	public UserRepositoryAdapter(JpaUserRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public Optional<User> findById(Long id) { return jpaRepository.findById(id); }

	@Override
	public User save(User user) { return jpaRepository.save(user); }
}
