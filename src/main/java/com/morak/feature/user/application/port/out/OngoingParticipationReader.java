package com.morak.feature.user.application.port.out;

/** 탈퇴 가능 여부 판단용 참여 현황. 다른 도메인(participation)은 이 포트로만 조회한다. */
public interface OngoingParticipationReader {

	/** 진행 중이거나 시작을 기다리는 챌린지 참여 건수(방장 포함). 승인 대기와 종료된 방은 제외한다. */
	int countOngoing(long userId);
}
