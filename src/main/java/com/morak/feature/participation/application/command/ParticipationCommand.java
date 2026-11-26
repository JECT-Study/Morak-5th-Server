package com.morak.feature.participation.application.command;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 다른 모듈(챌린지 등)에 공개하는 참가자 변경 API.
 *
 * <p>모두 호출한 쪽의 트랜잭션에 참여한다. 트랜잭션 없이 호출하면 예외가 난다.
 */
public interface ParticipationCommand {

	/** 개설자를 HOST·ACTIVE 참가자로 등록한다. 같은 챌린지에 이미 참가 정보가 있으면 예외. */
	long registerHost(long challengeId, long userId, Instant now, int pointAmount);

	/**
	 * 사용자의 승인 대기 신청을 모두 무효(INVALIDATED)로 바꾼다. 포인트가 걸린 신청이 하나라도 있으면 반환 처리가 필요하므로
	 * 아무것도 바꾸지 않고 empty를 반환한다.
	 */
	Optional<List<InvalidatedApplication>> invalidatePendingWithoutPoints(long userId);

	/**
	 * 방장 또는 부방장이 챌린지를 떠날 때 역할을 넘긴다 (위키 §4.3 방장·부방장 승계).
	 *
	 * <ul>
	 *   <li>방장이 떠나면 부방장이 방장이 되고, 남은 참여자 중 합류 순서가 가장 빠른 사람이 새 부방장이 된다.
	 *       챌린지의 방장(owner)도 함께 바꾼다.
	 *   <li>부방장이 떠나면 남은 참여자 중 합류 순서가 가장 빠른 사람이 새 부방장이 된다.
	 *   <li>떠나는 사람은 일반 참가자(MEMBER)로 내린다. 퇴장 같은 상태 변경은 호출한 쪽이 한다.
	 *   <li>일반 참가자가 떠나면 아무것도 바꾸지 않는다.
	 * </ul>
	 *
	 * 부방장이 없는 상태에서 방장이 떠나는 경우의 대체 절차는 위키 O-9(미정)라서 아무것도 바꾸지 않고
	 * {@link RoleHandOver#successorMissing()}으로 알린다.
	 */
	RoleHandOver handOverRoles(long challengeId, long departingParticipationId);

	/**
	 * 실패 누적으로 퇴장시킨다(위키 §4.4 퇴장·조기 종료, §4.9 ⑥). 퇴장 기준 판단은 호출한 쪽이 한다.
	 *
	 * <ul>
	 *   <li>챌린지를 잠근 뒤 참여 중(ACTIVE)인 대상을 한 명씩 승계({@link #handOverRoles}) 후 EXPELLED로 바꾼다.
	 *       이미 ACTIVE가 아닌 대상은 건너뛰므로 여러 번 호출해도 결과가 같다.
	 *   <li>퇴장 후 공개 챌린지에 참여 중인 사람이 2명 이하면 같은 트랜잭션에서 남은 사람을 EARLY_ENDED로 바꾸고
	 *       챌린지를 조기 종료한다(종료일 = {@code today}, FINALIZING).
	 *   <li>포인트는 건드리지 않는다(퇴장자 전액 차감은 정산 몫, §4.5).
	 * </ul>
	 */
	ExpelOutcome expel(long challengeId, Collection<Long> participationIds, LocalDate today, Instant now);

	/**
	 * 결과 확정(위키 §4.5)으로 챌린지에 남은 참여 중(ACTIVE) 참가자를 모두 COMPLETED(`end_reason=COMPLETED`)로 바꾸고 그 참가 ID를
	 * 돌려준다. 퇴장자·조기 종료 남은 사람(EARLY_ENDED)은 그대로 둔다. 챌린지를 잠근 뒤에 호출한다. 다시 호출하면 빈 목록이다.
	 */
	List<Long> completeActive(long challengeId, Instant now);

	/**
	 * 퇴장 결과.
	 *
	 * @param earlyEndedUserIds 조기 종료로 EARLY_ENDED가 된 남은 참여자. 조기 종료하지 않았으면 비어 있다
	 */
	record ExpelOutcome(List<ExpelledMember> expelled, List<Long> earlyEndedUserIds) {}

	record ExpelledMember(long participationId, long userId) {}

	/**
	 * 시작 전 폐기에 대비해 포인트가 걸린 참가자(PENDING·ACTIVE)의 포인트 계정을 사용자 번호 오름차순으로 잠근다.
	 * 락 순서(포인트 계정 → 챌린지 → 참가자)를 지키려고 챌린지를 잠그기 전에 호출한다. 잠근 사용자 번호를 돌려준다.
	 */
	Set<Long> lockPointAccountsForDiscard(long challengeId);

	/**
	 * 시작 전 폐기로 참가자(PENDING·ACTIVE) 전원을 DISCARDED로 바꾸고 걸린 포인트를 전액 돌려준다. 챌린지를 잠근 뒤에 호출한다.
	 * {@code lockedUserIds}에 없는 포인트 보유자가 있으면(그 사이 새로 지원) 아무것도 바꾸지 않고 409로 되돌린다.
	 * participation_result는 만들지 않는다(위키 §4.2).
	 */
	List<DiscardedParticipation> discardAll(long challengeId, String challengeTitle, Set<Long> lockedUserIds, Instant now);

	/**
	 * 시작 처리에 대비해 포인트가 걸린 승인 대기(PENDING) 신청자의 포인트 계정을 사용자 번호 오름차순으로 잠근다.
	 * 락 순서(포인트 계정 → 챌린지 → 참가자)를 지키려고 챌린지를 잠그기 전에 호출한다. 잠근 사용자 번호를 돌려준다.
	 */
	Set<Long> lockPendingPointAccounts(long challengeId);

	/**
	 * 챌린지가 시작될 때 남은 승인 대기(PENDING) 신청을 모두 거절(REJECTED)하고 걸린 포인트를 전액 돌려주며, 신청자에게 알림 #2를 보낸다.
	 * 챌린지를 잠근 뒤에 호출한다. {@code lockedUserIds}에 없는 포인트 보유 신청자가 있으면(그 사이 새로 지원) 아무것도 바꾸지 않고
	 * 409로 되돌린다. 위키 §9 O-21(PM 확정 2026-11-21).
	 */
	List<RejectedApplication> rejectPendingOnStart(long challengeId, String challengeTitle, Set<Long> lockedUserIds);

	/**
	 * 시작 처리로 거절된 신청.
	 *
	 * @param refundedPoint 돌려준 포인트. 포인트가 걸리지 않았으면 0
	 */
	record RejectedApplication(long participationId, long userId, int refundedPoint) {}

	/**
	 * 폐기된 참가.
	 *
	 * @param host 방장 본인 여부
	 * @param refundedPoint 돌려준 포인트. 포인트가 걸리지 않았으면 0
	 */
	record DiscardedParticipation(long participationId, long userId, boolean host, int refundedPoint) {}

	/**
	 * 정산이 확정될 때 참가자별 결과(participation_result)를 저장하고 새로 저장한 행 수를 돌려준다 (위키 §4.5).
	 *
	 * <ul>
	 *   <li>이미 결과가 있는 참가자는 건너뛰므로 여러 번 호출해도 결과가 같다.
	 *   <li>시작 전에 폐기됐거나 승인받지 못한 참가자는 행을 만들지 않는다.
	 *   <li>없는 참가자 ID가 있으면 아무것도 저장하지 않고 예외를 던진다.
	 *   <li>락 순서(포인트 계정 → 챌린지 → 참가자)는 호출한 정산 트랜잭션이 이미 지킨 상태여야 한다.
	 * </ul>
	 */
	int recordResults(List<ResultInput> inputs, Instant finalizedAt);

	/**
	 * 한 참가자의 결과 입력. 승인 수·대상 수·연속 인증 일수·완주 여부는 정산하는 쪽이 계산해서 넘긴다.
	 * 챌린지·사용자 ID는 참가자 행에서 읽으므로 받지 않는다. 인증률은 저장하는 쪽에서 계산한다.
	 */
	record ResultInput(
			long participationId, int approvedCount, int totalCount, int currentStreak, int maxStreak,
			boolean completed) {}

	/** 무효가 된 신청. {@code hostUserId}는 신청한 챌린지의 방장으로, 무효 알림을 받는다. */
	record InvalidatedApplication(long participationId, long challengeId, long hostUserId) {}

	/**
	 * 승계 결과. 바뀌지 않은 역할은 null이다.
	 *
	 * @param newHostUserId 새 방장의 사용자 번호
	 * @param newDeputyUserId 새 부방장의 사용자 번호
	 * @param successorMissing 방장이 떠나는데 부방장이 없어 승계하지 못했다 (위키 O-9)
	 */
	record RoleHandOver(Long newHostUserId, Long newDeputyUserId, boolean successorMissing) {

		public static RoleHandOver unchanged() {
			return new RoleHandOver(null, null, false);
		}
	}
}
