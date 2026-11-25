package com.morak.feature.user.domain.entity;

import com.morak.common.entity.BaseEntity;
import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.user.domain.exception.UserErrorCode;
import com.morak.feature.user.domain.vo.Interest;
import com.morak.feature.user.domain.vo.OnboardingProfile;
import com.morak.feature.user.domain.vo.TermsAgreements;
import com.morak.feature.user.domain.vo.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "users")
@DynamicUpdate
@Getter
public class User extends BaseEntity {

	private static final String STATUS_ACTIVE = "ACTIVE";
	private static final String STATUS_WITHDRAWN = "WITHDRAWN";
	private static final String ROLE_ADMIN = "ADMIN";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(length = 255)
	private String email;

	@Column(length = 20)
	private String nickname;

	@Column(name = "profile_image_url", length = 500)
	private String profileImageUrl;

	@Column(length = 255)
	private String bio;

	@Column(name = "profile_link_url", length = 255)
	private String profileLinkUrl;

	@Getter(AccessLevel.NONE)
	@ElementCollection
	@CollectionTable(name = "user_interest", joinColumns = @JoinColumn(name = "user_id"))
	@Column(name = "interest_code", length = 30, nullable = false)
	private Set<String> interests = new HashSet<>();

	@Column(nullable = false, length = 20)
	private String status = STATUS_ACTIVE;

	/** USER(일반) / ADMIN(운영진). 위키 §5. */
	@Column(nullable = false, length = 20)
	private String role = "USER";

	@Column(name = "terms_agreed_at")
	private LocalDateTime termsAgreedAt;

	@Column(name = "marketing_agreed", nullable = false)
	private boolean marketingAgreed;

	@Column(name = "onboarded_at")
	private LocalDateTime onboardedAt;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	protected User() {}

	public User(String email, String profileImageUrl) {
		this.email = email;
		this.profileImageUrl = profileImageUrl;
	}

	public UserId userId() { return new UserId(id); }
	public boolean hasAgreedToTerms() { return termsAgreedAt != null; }
	public boolean isOnboarded() { return onboardedAt != null; }
	public boolean isAdmin() { return ROLE_ADMIN.equals(role); }
	public boolean isActive() { return STATUS_ACTIVE.equals(status) && deletedAt == null; }
	public List<String> interestCodes() { return interests.stream().sorted().toList(); }

	public void agreeToTerms(TermsAgreements agreements, LocalDateTime now) {
		if (hasAgreedToTerms()) {
			throw new BusinessException(UserErrorCode.TERMS_ALREADY_AGREED);
		}
		if (!agreements.acceptsAllRequired()) {
			throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
		}
		termsAgreedAt = now;
		marketingAgreed = agreements.marketingAgreed();
	}

	public void updateMarketingAgreement(boolean agreed) {
		if (!hasAgreedToTerms()) {
			throw new BusinessException(UserErrorCode.TERMS_AGREEMENT_REQUIRED);
		}
		marketingAgreed = agreed;
	}

	public void completeOnboarding(OnboardingProfile profile, LocalDateTime now) {
		if (isOnboarded()) {
			throw new BusinessException(UserErrorCode.ALREADY_ONBOARDED);
		}
		if (!hasAgreedToTerms()) {
			throw new BusinessException(UserErrorCode.TERMS_AGREEMENT_REQUIRED);
		}
		applyProfile(profile);
		onboardedAt = now;
	}

	/** 온보딩 완료 여부는 SecurityConfig가 요청 단계에서 막는다. */
	public void updateProfile(OnboardingProfile profile) {
		applyProfile(profile);
	}

	/** @return 바꾸기 전 사진 URL. 직접 올린 사진이면 호출자가 저장소에서 지운다 */
	public String changeProfileImage(String profileImageUrl) {
		String previous = this.profileImageUrl;
		this.profileImageUrl = profileImageUrl;
		return previous;
	}

	private void applyProfile(OnboardingProfile profile) {
		nickname = profile.nickname().value();
		bio = profile.bio();
		profileLinkUrl = profile.profileLinkUrl();
		interests.clear();
		profile.interests().stream().map(Interest::name).forEach(interests::add);
	}

	/**
	 * 회원 탈퇴. 개인정보를 비식별화하고 상태를 WITHDRAWN으로 바꾼다. 위키 §4.1.
	 * 탈퇴 가능 여부(진행·시작 대기 챌린지)는 호출 전에 판단한다.
	 * TODO(D-3): 비식별화 대상은 PM 확인 전 제안값이다. 마케팅 수신 동의는 위키에 정해진 바가 없어 건드리지 않는다(O-18).
	 */
	public void withdraw(LocalDateTime now) {
		if (!isActive()) {
			throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
		}
		email = null;
		nickname = null;
		profileImageUrl = null;
		bio = null;
		profileLinkUrl = null;
		interests.clear();
		status = STATUS_WITHDRAWN;
		deletedAt = now;
	}

	/** 프로필 사진은 가입 때만 소셜 사진으로 채운다. 이후 로그인은 직접 올린 사진을 덮어쓰지 않는다(D-33). */
	public void updateSocialProfile(String email) {
		if (email != null && !email.isBlank()) {
			this.email = email;
		}
	}
}
