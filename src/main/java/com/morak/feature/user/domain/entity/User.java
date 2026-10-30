package com.morak.feature.user.domain.entity;

import com.morak.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
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

	public Long getId() { return id; }
	public String getEmail() { return email; }
	public String getNickname() { return nickname; }
	public String getProfileImageUrl() { return profileImageUrl; }
	public LocalDateTime getOnboardedAt() { return onboardedAt; }
	public boolean isOnboarded() { return onboardedAt != null; }
	public boolean isActive() { return "ACTIVE".equals(status) && deletedAt == null; }

	public void updateSocialProfile(String email, String profileImageUrl) {
		if (email != null && !email.isBlank()) {
			this.email = email;
		}
		if (profileImageUrl != null && !profileImageUrl.isBlank()) {
			this.profileImageUrl = profileImageUrl;
		}
	}
}
