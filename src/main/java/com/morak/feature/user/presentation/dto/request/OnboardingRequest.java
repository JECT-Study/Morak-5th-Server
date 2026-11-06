package com.morak.feature.user.presentation.dto.request;

import com.morak.feature.user.domain.vo.Interest;
import com.morak.feature.user.domain.vo.OnboardingProfile;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record OnboardingRequest(
		String nickname,
		String bio,
		String profileLinkUrl,
		@ArraySchema(minItems = 1, schema = @Schema(implementation = Interest.class)) List<String> interests) {

	public OnboardingProfile toProfile() {
		return OnboardingProfile.from(nickname, bio, profileLinkUrl, interests);
	}
}
