package com.morak.feature.auth.application.port.out;

import com.morak.feature.auth.domain.vo.AuthUserId;
import java.util.Optional;

public interface UserAuthPort {
	Optional<UserState> findUser(AuthUserId userId);

	UserState createUser(String email, String profileImageUrl);

	Optional<UserState> updateSocialProfile(AuthUserId userId, String email, String profileImageUrl);

	record UserState(AuthUserId userId, boolean active, boolean onboarded) {}
}
