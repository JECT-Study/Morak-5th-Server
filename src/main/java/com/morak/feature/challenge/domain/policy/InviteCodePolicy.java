package com.morak.feature.challenge.domain.policy;

import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * 초대 코드 형식(위키 §4.2, D-15). 발급과 조회가 같은 정의를 쓰도록 한 곳에서 관리한다.
 * 숫자 전용인지 영문을 포함하는지는 미정(§9 O-5)이며, 현재는 숫자 6자리만 발급한다. 바꿀 때는 이 클래스만 고친다.
 */
public final class InviteCodePolicy {

	public static final int LENGTH = 6;
	private static final int BOUND = 1_000_000;

	private InviteCodePolicy() {
	}

	public static String generate(RandomGenerator random) {
		return String.format(Locale.ROOT, "%0" + LENGTH + "d", random.nextInt(BOUND));
	}

	/** 발급될 수 있는 형식인지. 형식이 다르면 조회하지 않고 유효하지 않은 코드로 본다. */
	public static boolean matchesFormat(String code) {
		if (code == null || code.length() != LENGTH) {
			return false;
		}
		for (int i = 0; i < LENGTH; i++) {
			char c = code.charAt(i);
			if (c < '0' || c > '9') {
				return false;
			}
		}
		return true;
	}
}
