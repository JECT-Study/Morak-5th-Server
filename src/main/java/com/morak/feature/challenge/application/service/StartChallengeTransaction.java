package com.morak.feature.challenge.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.application.port.out.ChallengeParticipationReader;
import com.morak.feature.challenge.application.port.out.ChallengeStartHeldNotice;
import com.morak.feature.challenge.domain.entity.Challenge;
import com.morak.feature.challenge.domain.enums.ChallengeStatus;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import com.morak.feature.challenge.domain.repository.ChallengeRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 챌린지 한 건의 시작 처리(위키 §4.2, §4.9 ②). 챌린지 하나당 트랜잭션 하나다.
 * 최소 인원을 채웠으면 시작하고, 모집 중인데 미달이면 시작 보류로 바꾸고 방장에게 알림 #4를 보낸다.
 * 잠그는 것은 챌린지 행 하나뿐이다(포인트 변동이 없어 락 순서 규칙에 걸리지 않는다).
 * 알림이 실패하면 상태 전이도 롤백되고 다음 실행이 다시 처리한다(폐기·재설정과 같은 정책).
 *
 * <p>TODO(D-29): 합류 인원은 참가 집계(승인 대기 제외)로 세고, 누락 보정 건의 마감이 이미 지났으면 알림 #4를 보내지 않는다.
 * 시작할 때 남은 승인 대기 신청은 건드리지 않는다(O-21).
 */
@Service
public class StartChallengeTransaction {

	private static final Logger log = LoggerFactory.getLogger(StartChallengeTransaction.class);

	private final ChallengeRepository challenges;
	private final ChallengeParticipationReader participants;
	private final Optional<ChallengeStartHeldNotice> notice;

	public StartChallengeTransaction(ChallengeRepository challenges, ChallengeParticipationReader participants,
			Optional<ChallengeStartHeldNotice> notice) {
		this.challenges = challenges;
		this.participants = participants;
		this.notice = notice;
	}

	@Transactional
	public Outcome process(long challengeId, LocalDate today) {
		Optional<Challenge> found = challenges.findByIdForUpdate(challengeId);
		if (found.isEmpty()) {
			return Outcome.UNCHANGED;
		}
		Challenge challenge = found.get();
		// 후보를 고른 뒤 잠그기 전에 폐기·재설정·시작됐을 수 있으므로 잠근 뒤 다시 확인한다.
		if (!challenge.isStartDue(today)) {
			return Outcome.UNCHANGED;
		}
		// 승인도 챌린지 행을 잠근 뒤 처리하므로, 잠근 뒤 센 인원은 그 사이 승인을 빠뜨리지 않는다.
		int joined = participants.countJoined(challengeId);
		if (challenge.hasEnoughMembers(joined)) {
			challenge.start(today);
			return Outcome.STARTED;
		}
		if (challenge.getStatus() == ChallengeStatus.START_HELD) {
			return Outcome.UNCHANGED;
		}
		challenge.holdStart(today);
		LocalDate deadline = challenge.recruitDeadlineDate();
		if (deadline.isBefore(today)) {
			// 서버가 멈춰 있어 늦게 처리한 경우다. 이미 지난 기한은 안내하지 않는다. 자동 폐기(③, #36 후속)가 들어오면 같은 배치에서 폐기된다.
			log.warn("챌린지 시작 보류(기한 경과로 알림 생략): challengeId={}, startDate={}, today={}", challengeId,
					challenge.getStartDate(), today);
			return Outcome.HELD;
		}
		notice.orElseThrow(() -> new BusinessException(ChallengeErrorCode.START_INTEGRATION_UNAVAILABLE))
				.startHeld(challenge.getOwnerUserId(), challengeId, challenge.getTitle(), deadline, challenge.getStartDate());
		return Outcome.HELD;
	}

	public enum Outcome {
		STARTED, HELD, UNCHANGED
	}
}
