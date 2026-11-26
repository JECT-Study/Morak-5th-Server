package com.morak.feature.challenge.domain.enums;

public enum ChallengeVisibility {
	PUBLIC(3), PRIVATE(2);

	private final int minParticipants;

	ChallengeVisibility(int minParticipants) {
		this.minParticipants = minParticipants;
	}

	/** 시작에 필요한 최소 인원(방장 포함). 공개 3 / 비공개 2 (위키 §4.2). */
	public int minParticipants() { return minParticipants; }
}
