package com.morak.feature.challenge.domain.entity;

import com.morak.common.entity.BaseEntity;
import com.morak.feature.challenge.domain.enums.ChallengeCategory;
import com.morak.feature.challenge.domain.enums.ChallengeStatus;
import com.morak.feature.challenge.domain.enums.ChallengeVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "challenge")
public class Challenge extends BaseEntity {

	@Getter
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long ownerUserId;
	private String title;

	@Enumerated(EnumType.STRING)
	private ChallengeVisibility visibility;

	@Enumerated(EnumType.STRING)
	private ChallengeCategory category;

	private String description;
	private String purpose;
	private String commonMission;
	private String verificationRules;
	private String expectations;
	@Column(length = 300)
	private String coverImageKey;

	@JdbcTypeCode(SqlTypes.CHAR)
	private String inviteCode;

	@Enumerated(EnumType.STRING)
	private ChallengeStatus status;

	private LocalDate startDate;
	private LocalDate endDate;
	private int capacity;
	private int currentCount;
	@Column(name = "point_amount", nullable = false)
	private int pointAmount;

	protected Challenge() {}

	public Challenge(long ownerUserId, String title, ChallengeVisibility visibility, ChallengeCategory category,
			String description, String purpose, String commonMission, String verificationRules, String expectations, String inviteCode,
			LocalDate startDate, LocalDate endDate, int capacity, int pointAmount, String coverImageKey) {
		this.ownerUserId = ownerUserId;
		this.title = title;
		this.visibility = visibility;
		this.category = category;
		this.description = description;
		this.purpose = purpose;
		this.commonMission = commonMission;
		this.verificationRules = verificationRules;
		this.expectations = expectations;
		this.coverImageKey = coverImageKey;
		this.inviteCode = inviteCode;
		this.startDate = startDate;
		this.endDate = endDate;
		this.capacity = capacity;
		this.pointAmount = pointAmount;
		this.status = ChallengeStatus.RECRUITING;
		this.currentCount = 1;
	}
}
