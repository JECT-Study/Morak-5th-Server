package com.morak.feature.user.infrastructure.persistence;

import com.morak.common.exception.BusinessException;
import com.morak.feature.user.domain.exception.UserErrorCode;
import com.morak.feature.user.domain.vo.Nickname;
import com.morak.feature.user.domain.vo.UserId;
import com.morak.feature.user.domain.entity.User;
import com.morak.feature.user.domain.repository.UserRepository;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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
	public Optional<User> findByIdForUpdate(UserId id) { return jpaRepository.findLockedById(id.value()); }

	@Override
	public boolean existsByNickname(Nickname nickname) { return jpaRepository.existsByNickname(nickname.value()); }

	@Override
	public User save(User user) { return jpaRepository.save(user); }

	@Override
	public void flushOnboarding() {
		try {
			jpaRepository.flush();
		} catch (DataIntegrityViolationException exception) {
			for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
				if (cause instanceof ConstraintViolationException violation
						&& "uk_users_nickname".equals(violation.getConstraintName())) {
					throw new BusinessException(UserErrorCode.NICKNAME_ALREADY_IN_USE);
				}
			}
			throw exception;
		}
	}
}
