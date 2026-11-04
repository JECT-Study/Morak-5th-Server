package com.morak.feature.user.infrastructure.persistence;

import com.morak.feature.user.domain.vo.Nickname;
import com.morak.feature.user.domain.vo.UserId;
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
	public Optional<User> findById(UserId id) { return jpaRepository.findById(id.value()); }

	@Override
	public boolean existsByNickname(Nickname nickname) { return jpaRepository.existsByNickname(nickname.value()); }

	@Override
	public User save(User user) { return jpaRepository.save(user); }
}
