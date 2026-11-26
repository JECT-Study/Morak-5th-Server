package com.morak.feature.challenge.domain.policy;

import java.util.random.RandomGenerator;

/**
 * 초대 코드 형식(위키 §4.2, D-15, O-5). 발급과 조회가 같은 정의를 쓰도록 한 곳에서 관리한다.
 * 영문 대문자와 숫자 6자리다. 소문자는 발급하지 않고 조회에서도 받지 않는다(입력 변환은 화면에서 한다).
 * 숫자만 쓰던 기존 코드도 같은 형식에 들어가 그대로 쓸 수 있다.
 */
public final class InviteCodePolicy {

	public static final int LENGTH = 6;
	private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

	private InviteCodePolicy() {
	}

	public static String generate(RandomGenerator random) {
		char[] code = new char[LENGTH];
		for (int i = 0; i < LENGTH; i++) {
			code[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
		}
		return new String(code);
	}

	/** 발급될 수 있는 형식인지. 형식이 다르면 조회하지 않고 유효하지 않은 코드로 본다. */
	public static boolean matchesFormat(String code) {
		if (code == null || code.length() != LENGTH) {
			return false;
		}
		for (int i = 0; i < LENGTH; i++) {
			char c = code.charAt(i);
			if (!(c >= '0' && c <= '9') && !(c >= 'A' && c <= 'Z')) {
				return false;
			}
		}
		return true;
	}
}
