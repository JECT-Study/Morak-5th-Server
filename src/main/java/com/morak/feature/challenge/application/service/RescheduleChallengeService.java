package com.morak.feature.challenge.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.challenge.application.dto.command.RescheduleChallengeCommand;
import com.morak.feature.challenge.application.dto.result.RescheduledChallenge;
import com.morak.feature.challenge.application.port.out.ChallengeParticipationReader;
import com.morak.feature.challenge.application.port.out.ChallengeParticipationReader.WaitingParticipant;
import com.morak.feature.challenge.application.port.out.ChallengeScheduleResetNotice;
import com.morak.feature.challenge.domain.entity.Challenge;
import com.morak.feature.challenge.domain.enums.ChallengeStatus;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import com.morak.feature.challenge.domain.repository.ChallengeRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방장의 시작 보류 기간 재설정(위키 §4.2). 상태·기간 변경과 알림 #5를 한 트랜잭션에서 처리한다.
 * 잠그는 것은 챌린지 행 하나뿐이다(포인트 변동이 없어 락 순서 규칙에 걸리지 않는다).
 * 알림이 실패하면 재설정도 롤백된다(폐기와 같은 정책).
 *
 * <p>TODO(D-27): 경로, 새 시작일 "내일부터"·최소 7일(개설과 같은 규칙), 횟수 제한 없음, 마감 이후에도 허용,
 * 알림 대상(방장을 뺀 ACTIVE·PENDING), 같은 기간 재호출의 멱등 응답은 위키에 없는 제안이다.
 */
@Service
public class RescheduleChallengeService {

	private final ChallengeRepository challenges;
	private final ChallengeParticipationReader participants;
	private final Optional<ChallengeScheduleResetNotice> notice;
	private final Clock clock;

	public RescheduleChallengeService(ChallengeRepository challenges, ChallengeParticipationReader participants,
			Optional<ChallengeScheduleResetNotice> notice, Clock clock) {
		this.challenges = challenges;
		this.participants = participants;
		this.notice = notice;
		this.clock = clock;
	}

	@Transactional
	public RescheduledChallenge reschedule(long userId, long challengeId, RescheduleChallengeCommand command) {
		Challenge challenge = challenges.findByIdForUpdate(challengeId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		if (challenge.getOwnerUserId() != userId) {
			throw new BusinessException(ChallengeErrorCode.NOT_OWNER);
		}
		// 응답이 유실돼 같은 요청을 다시 보낸 경우: 이미 같은 기간으로 되돌아와 있으면 알림 없이 같은 응답을 준다.
		if (challenge.getStatus() == ChallengeStatus.RECRUITING && command.startDate().equals(challenge.getStartDate())
				&& command.endDate().equals(challenge.getEndDate())) {
			return toResult(challenge, challengeId);
		}
		challenge.reschedule(command.startDate(), command.endDate(), clock.instant());
		// 챌린지를 잠근 뒤에 읽으므로, 그 사이 승인·지원한 참가자도 빠지지 않는다.
		List<WaitingParticipant> recipients = participants.findWaitingParticipants(challengeId);
		if (!recipients.isEmpty()) {
			ChallengeScheduleResetNotice notices = notice
					.orElseThrow(() -> new BusinessException(ChallengeErrorCode.RESCHEDULE_INTEGRATION_UNAVAILABLE));
			for (WaitingParticipant recipient : recipients) {
				notices.scheduleReset(recipient.userId(), challengeId, recipient.participationId(), challenge.getTitle(),
						command.startDate(), command.endDate());
			}
		}
		return toResult(challenge, challengeId);
	}

	private static RescheduledChallenge toResult(Challenge challenge, long challengeId) {
		return new RescheduledChallenge(challengeId, challenge.getStatus().name(), challenge.getStartDate(),
				challenge.getEndDate());
	}
}
