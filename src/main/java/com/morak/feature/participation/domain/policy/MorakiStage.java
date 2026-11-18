package com.morak.feature.participation.domain.policy;

import java.util.Optional;

/**
 * 개인 인증률로 정하는 모락이 단계(위키 §4.1). 인증 기록이 없으면 단계가 없고, 화면은 기본 캐릭터를 보여 준다.
 *
 * <p>경계값은 높은 단계에 속한다(정확히 30%→2, 60%→3, 85%→4, 95%→5). 비교는 반올림하지 않은 비율로 정수 연산한다.
 * TODO(D-25): 반올림한 표시 비율이 아니라 반올림 전 비율로 단계를 정하는 것은 제안이다.
 */
public enum MorakiStage {
	STAGE_1(1, 0),
	STAGE_2(2, 30),
	STAGE_3(3, 60),
	STAGE_4(4, 85),
	STAGE_5(5, 95);

	private final int level;
	private final int minPercent;

	MorakiStage(int level, int minPercent) {
		this.level = level;
		this.minPercent = minPercent;
	}

	public int level() {
		return level;
	}

	/** 누적 승인·의무 횟수로 단계를 구한다. 의무가 0이면(인증 기록 없음) empty. */
	public static Optional<MorakiStage> of(int approvedCount, int obligationCount) {
		if (obligationCount <= 0) {
			return Optional.empty();
		}
		long approvedTimes100 = 100L * approvedCount;
		MorakiStage result = STAGE_1;
		for (MorakiStage stage : values()) {
			if (approvedTimes100 >= (long) stage.minPercent * obligationCount) {
				result = stage;
			}
		}
		return Optional.of(result);
	}
}
