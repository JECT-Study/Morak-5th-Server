package com.morak.feature.challenge.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "challenge")
public class Challenge {

	public enum Visibility {
		PUBLIC, PRIVATE
	}

	public enum Category {
		STUDY, EXERCISE, CAREER, LIFESTYLE, READING
	}

	public enum Status {
		RECRUITING, START_HELD, IN_PROGRESS, FINALIZING, ENDED, DISCARDED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private long ownerUserId;
	private String title;

	@Enumerated(EnumType.STRING)
	private Visibility visibility;

	@Enumerated(EnumType.STRING)
	private Category category;

	private String purpose;
	private String commonMission;
	private String verificationRules;
	private String expectations;

	@JdbcTypeCode(SqlTypes.CHAR)
	private String inviteCode;

	@Enumerated(EnumType.STRING)
	private Status status;

	private LocalDate startDate;
	private LocalDate endDate;
	private int capacity;
	private int currentCount;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	protected Challenge() {}

	public Challenge(long ownerUserId, String title, Visibility visibility, Category category, String purpose,
			String commonMission, String verificationRules, String expectations, String inviteCode,
			LocalDate startDate, LocalDate endDate, int capacity, Instant now) {
		this.ownerUserId = ownerUserId;
		this.title = title;
		this.visibility = visibility;
		this.category = category;
		this.purpose = purpose;
		this.commonMission = commonMission;
		this.verificationRules = verificationRules;
		this.expectations = expectations;
		this.inviteCode = inviteCode;
		this.startDate = startDate;
		this.endDate = endDate;
		this.capacity = capacity;
		this.status = Status.RECRUITING;
		this.currentCount = 1;
		this.createdAt = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
		this.updatedAt = createdAt;
	}

	public Long getId() {
		return id;
	}
}
