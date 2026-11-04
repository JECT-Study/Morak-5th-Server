package com.morak.feature.user.domain.entity;

import com.morak.common.entity.BaseEntity;
import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.user.domain.exception.UserErrorCode;
import com.morak.feature.user.domain.vo.TermsAgreements;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "users")
@DynamicUpdate
@Getter
public class User extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(length = 255)
	private String email;

	@Column(length = 20)
	private String nickname;

	@Column(name = "profile_image_url", length = 500)
	private String profileImageUrl;

	@Column(nullable = false, length = 20)
	private String status = "ACTIVE";

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

	public boolean hasAgreedToTerms() { return termsAgreedAt != null; }
	public boolean isOnboarded() { return onboardedAt != null; }
	public boolean isActive() { return "ACTIVE".equals(status) && deletedAt == null; }

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

	public void updateSocialProfile(String email, String profileImageUrl) {
		if (email != null && !email.isBlank()) {
			this.email = email;
		}
		if (profileImageUrl != null && !profileImageUrl.isBlank()) {
			this.profileImageUrl = profileImageUrl;
		}
	}
}
