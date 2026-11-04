package com.morak.feature.user.application.service;

import com.morak.feature.user.application.dto.result.UserAuthInfo;
import com.morak.feature.user.domain.entity.User;
import com.morak.feature.user.domain.repository.UserRepository;
import com.morak.feature.user.domain.vo.UserId;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {
	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public Optional<UserAuthInfo> findUser(UserId userId) {
		return userRepository.findById(userId).map(UserAuthInfo::from);
	}

	@Transactional
	public UserAuthInfo createUser(String email, String profileImageUrl) {
		User user = userRepository.save(new User(email, profileImageUrl));
		return UserAuthInfo.from(user);
	}

	@Transactional
	public Optional<UserAuthInfo> updateSocialProfile(UserId userId, String email, String profileImageUrl) {
		return userRepository.findById(userId).map(user -> {
			if (user.isActive()) {
				user.updateSocialProfile(email, profileImageUrl);
			}
			return UserAuthInfo.from(user);
		});
	}
}
