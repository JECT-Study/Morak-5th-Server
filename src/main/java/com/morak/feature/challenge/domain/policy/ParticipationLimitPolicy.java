package com.morak.feature.challenge.domain.policy;

import java.util.function.IntSupplier;

/** 동시 참여 한도 정책(위키 §4.3). 한도 값과 적용 여부, 판단 기준을 한 곳에서 관리한다. */
public final class ParticipationLimitPolicy {

	public static final int LIMIT = 5;

	// HOTFIX: 동시 참여 5개 제한을 임시로 해제했다(위키 §9 O-17, D-19). 다시 설정하려면 true로 되돌린다.
	private static final boolean ENABLED = false;

	private ParticipationLimitPolicy() {
	}

	/** 이미 한도에 도달해 더 참여할 수 없는지. */
	public static boolean isExceeded(int ongoing) {
		return ENABLED && ongoing >= LIMIT;
	}

	/** 방 하나를 더 얻으면 한도에 도달하는지. 이때 대기 신청을 무효화한다. */
	public static boolean reachesLimitWithOneMore(int ongoing) {
		return ENABLED && ongoing + 1 == LIMIT;
	}

	/** 더 참여할 수 있는지. 제한이 꺼져 있으면 건수를 조회하지 않는다. */
	public static boolean canJoinMore(IntSupplier ongoing) {
		return !ENABLED || ongoing.getAsInt() < LIMIT;
	}
}
