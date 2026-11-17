package com.morak.feature.notification.domain.entity;

/** 알림 종류 11종 (위키 §4.8 표의 #1~#11 순서). 이름은 호출 도메인과 합의한 뒤 확정한다. */
public enum NotificationType {
	/** #1 참가 신청 승인 */
	PARTICIPATION_APPROVED,
	/** #2 참가 신청 거절 (정원 마감 자동 거절 포함) */
	PARTICIPATION_REJECTED,
	/** #3 참여 한도 초과로 신청 무효화 */
	APPLICATION_INVALIDATED,
	/** #4 예정 시작일 최소 인원 미달 */
	START_HELD,
	/** #5 방장이 기간 재설정 */
	SCHEDULE_RESET,
	/** #6 방장이 시작 전 폐기 */
	CHALLENGE_DISCARDED,
	/** #7 기한 내 미선택 자동 폐기 */
	CHALLENGE_AUTO_DISCARDED,
	/** #8 첫 부방장 지정 */
	DEPUTY_ASSIGNED,
	/** #9 인증 판정 기한 초과 경고 */
	JUDGE_OVERDUE_WARNING,
	/** #10 개인 실패 누적으로 퇴장 예정 */
	EXPEL_NOTICED,
	/** #11 공개 챌린지 조기 종료 예정 */
	EARLY_END_NOTICED
}
