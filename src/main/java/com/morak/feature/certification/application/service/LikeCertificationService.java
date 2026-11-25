package com.morak.feature.certification.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.certification.application.dto.result.CertificationLike;
import com.morak.feature.certification.application.port.out.CertificationFeedTargets;
import com.morak.feature.certification.application.port.out.CertificationLikeStore;
import com.morak.feature.certification.domain.exception.CertificationErrorCode;
import java.time.Clock;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 인증 좋아요 누르기·취소(위키 §4.4: 좋아요는 성공 여부에 영향 없다). 포인트가 오가지 않아 행을 잠그지 않고, 중복은 유니크 인덱스가 막는다.
 * 같은 요청을 여러 번 보내도 결과가 같다.
 *
 * <p>TODO(D-36): 누를 수 있는 사람(피드 조회자와 같음)·대상(승인된 인증, 본인 것 포함)·경로는 제안값이다. 퇴장자는 O-16이 정해질 때까지
 * 막는다.
 */
@Service
public class LikeCertificationService {

	private static final Logger log = LoggerFactory.getLogger(LikeCertificationService.class);
	static final Set<String> LIKEABLE_STATUSES = Set.of("APPROVED", "AUTO_APPROVED");

	private final CertificationFeedTargets targets;
	private final CertificationLikeStore store;
	private final Clock clock;

	public LikeCertificationService(CertificationFeedTargets targets, CertificationLikeStore store, Clock clock) {
		this.targets = targets;
		this.store = store;
		this.clock = clock;
	}

	public CertificationLike like(long userId, long challengeId, long certificationId) {
		check(userId, challengeId, certificationId);
		store.like(certificationId, userId, clock.instant());
		return new CertificationLike(certificationId, true, store.count(certificationId));
	}

	public CertificationLike unlike(long userId, long challengeId, long certificationId) {
		check(userId, challengeId, certificationId);
		store.unlike(certificationId, userId);
		return new CertificationLike(certificationId, false, store.count(certificationId));
	}

	private void check(long userId, long challengeId, long certificationId) {
		if (!targets.challengeExists(challengeId)) {
			throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
		}
		boolean member = targets.findViewer(challengeId, userId)
				.filter(viewer -> ReadCertificationFeedService.VIEWABLE_STATUSES.contains(viewer.status()))
				.isPresent();
		if (!member) {
			log.warn("인증 좋아요 거절: 참여 확정자가 아니다. challengeId={} userId={}", challengeId, userId);
			throw new BusinessException(CertificationErrorCode.LIKE_NOT_MEMBER);
		}
		// 승인 전 인증은 다른 팀원에게 보이지 않으므로 없는 것과 같게 응답한다.
		if (!store.findStatus(challengeId, certificationId).filter(LIKEABLE_STATUSES::contains).isPresent()) {
			log.warn("인증 좋아요 거절: 승인된 인증이 아니다. challengeId={} certificationId={}", challengeId, certificationId);
			throw new BusinessException(CertificationErrorCode.LIKE_TARGET_NOT_FOUND);
		}
	}
}
