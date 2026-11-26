package com.morak.feature.certification.domain.vo;

/**
 * 포인트 없는 챌린지에서 인증을 판정하는 역할(위키 §4.4 판정 담당). 판정자는 저장하지 않고 제출자의 참가 role로 계산한다(§5).
 *
 * <p>방장은 자신을 뺀 모든 팀원(부방장 포함)의 인증을, 부방장은 방장의 인증만 판정한다. 그래서 방장·부방장 모두 자기 인증은 판정할 수
 * 없다. 승계로 role이 바뀌면 미처리 인증도 새 담당에게 넘어간다. 판정 API(#86)와 정오 자동 승인·경고(#89)가 같이 쓴다.
 */
public enum CertificationJudge {
	HOST,
	DEPUTY;

	/** @param submitterRole 인증을 낸 참가자의 participation.role */
	public static CertificationJudge forSubmitter(String submitterRole) {
		return HOST.name().equals(submitterRole) ? DEPUTY : HOST;
	}

	/** @param judgeRole 판정하려는 참가자의 participation.role */
	public static boolean canJudge(String judgeRole, String submitterRole) {
		return forSubmitter(submitterRole).name().equals(judgeRole);
	}
}
