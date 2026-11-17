package com.morak.feature.user.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.user.application.port.out.LoginSessionRevoker;
import com.morak.feature.user.application.port.out.OngoingParticipationReader;
import com.morak.feature.user.domain.entity.User;
import com.morak.feature.user.domain.exception.UserErrorCode;
import com.morak.feature.user.domain.repository.UserRepository;
import com.morak.feature.user.domain.vo.UserId;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 탈퇴. 위키 §4.1 [제안, D-3]. */
@Service
public class WithdrawUserService {

	private static final Logger log = LoggerFactory.getLogger(WithdrawUserService.class);

	private final UserRepository users;
	private final OngoingParticipationReader participations;
	private final LoginSessionRevoker sessions;
	private final Clock clock;

	public WithdrawUserService(UserRepository users, OngoingParticipationReader participations,
			LoginSessionRevoker sessions, Clock clock) {
		this.users = users;
		this.participations = participations;
		this.sessions = sessions;
		this.clock = clock;
	}

	/**
	 * 사용자 행을 잠근 뒤 진행·시작 대기 챌린지가 없을 때만 탈퇴시킨다.
	 * 챌린지 개설도 같은 사용자 행을 잠그므로, 탈퇴 판단과 개설이 동시에 일어나도 직렬화된다.
	 */
	@Transactional
	public void withdraw(UserId userId) {
		User user = users.findByIdForUpdate(userId)
				.filter(User::isActive)
				.orElseThrow(() -> {
					log.warn("탈퇴 거절: 없거나 이미 탈퇴한 사용자다. userId={}", userId.value());
					return new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
				});
		if (participations.countOngoing(userId.value()) > 0) {
			log.warn("탈퇴 거절: 진행·시작 대기 챌린지가 있다. userId={}", userId.value());
			throw new BusinessException(UserErrorCode.WITHDRAWAL_NOT_ALLOWED);
		}
		// TODO(D-3): 승인 대기(PENDING) 신청, 포인트 잔액, 재가입 정책은 PM 확인 전이다. 지금은 건드리지 않는다.
		LocalDateTime now = LocalDateTime.now(clock);
		sessions.revokeAll(userId.value(), now);
		user.withdraw(now);
	}
}
