package com.morak.feature.user.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.user.application.dto.result.MyUserInfo;
import com.morak.feature.user.application.dto.result.UserAuthInfo;
import com.morak.feature.user.application.port.out.SignupPointGranter;
import com.morak.feature.user.domain.entity.User;
import com.morak.feature.user.domain.repository.UserRepository;
import com.morak.feature.user.domain.vo.Nickname;
import com.morak.feature.user.domain.vo.OnboardingProfile;
import com.morak.feature.user.domain.vo.TermsAgreements;
import com.morak.feature.user.domain.vo.UserId;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {
	private final UserRepository userRepository;
	private final SignupPointGranter signupPointGranter;

	public UserService(UserRepository userRepository, SignupPointGranter signupPointGranter) {
		this.userRepository = userRepository;
		this.signupPointGranter = signupPointGranter;
	}

	public Optional<UserAuthInfo> findUser(UserId userId) {
		return userRepository.findById(userId).map(UserAuthInfo::from);
	}

	public boolean isNicknameAvailable(Nickname nickname) {
		return !userRepository.existsByNickname(nickname);
	}

	public MyUserInfo getMyInfo(UserId userId) {
		return userRepository.findById(userId)
				.filter(User::isActive)
				.map(MyUserInfo::from)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
	}

	@Transactional
	public void agreeToTerms(UserId userId, TermsAgreements agreements) {
		getActiveUserForUpdate(userId).agreeToTerms(agreements, LocalDateTime.now());
	}

	@Transactional
	public void updateMarketingAgreement(UserId userId, boolean agreed) {
		getActiveUserForUpdate(userId).updateMarketingAgreement(agreed);
	}

	/**
	 * 온보딩 완료와 가입 포인트 지급을 한 트랜잭션으로 처리한다. 위키 §4.1.
	 * 사용자 행 잠금으로 동시 호출을 줄 세우므로 늦게 온 쪽은 ALREADY_ONBOARDED로 거절된다.
	 * 잠금 순서는 사용자 → 포인트 계정으로 챌린지 개설과 같다.
	 */
	@Transactional
	public MyUserInfo completeOnboarding(UserId userId, OnboardingProfile profile) {
		User user = getActiveUserForUpdate(userId);
		user.completeOnboarding(profile, LocalDateTime.now());
		userRepository.flushOnboarding();
		signupPointGranter.grant(userId.value());
		return MyUserInfo.from(user);
	}

	/** 마이페이지 프로필 수정. 닉네임·소개·링크·관심 분야를 요청 값으로 바꾼다. 프로필 사진은 다루지 않는다. */
	@Transactional
	public MyUserInfo updateProfile(UserId userId, OnboardingProfile profile) {
		User user = getActiveUserForUpdate(userId);
		user.updateProfile(profile);
		userRepository.flushOnboarding();
		return MyUserInfo.from(user);
	}

	/** 호출자의 트랜잭션이 끝날 때까지 활성 사용자의 행을 잠근다. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void lockActiveUser(UserId userId) {
		getActiveUserForUpdate(userId);
	}

	private User getActiveUserForUpdate(UserId userId) {
		return userRepository.findByIdForUpdate(userId)
				.filter(User::isActive)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
	}

	@Transactional
	public UserAuthInfo createUser(String email, String profileImageUrl) {
		User user = userRepository.save(new User(email, profileImageUrl));
		return UserAuthInfo.from(user);
	}

	/** 탈퇴와 같은 행 잠금을 쓴다. 잠금 없이 읽으면 탈퇴 커밋 직후 이메일이 다시 저장될 수 있다. */
	@Transactional
	public Optional<UserAuthInfo> updateSocialProfile(UserId userId, String email, String profileImageUrl) {
		return userRepository.findByIdForUpdate(userId).map(user -> {
			if (user.isActive()) {
				user.updateSocialProfile(email, profileImageUrl);
			}
			return UserAuthInfo.from(user);
		});
	}
}
