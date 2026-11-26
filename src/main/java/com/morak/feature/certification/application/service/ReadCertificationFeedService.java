package com.morak.feature.certification.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.certification.application.dto.result.CertificationFeed;
import com.morak.feature.certification.application.port.out.CertificationFeedStore;
import com.morak.feature.certification.application.port.out.CertificationFeedStore.FeedRow;
import com.morak.feature.certification.application.port.out.CertificationFeedTargets;
import com.morak.feature.certification.application.port.out.CertificationFeedTargets.Submitter;
import com.morak.feature.certification.application.port.out.CertificationFeedTargets.Viewer;
import com.morak.feature.certification.application.port.out.CertificationImageUrls;
import com.morak.feature.certification.domain.exception.CertificationErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 인증 피드(위키 §4.4: 피드에는 승인된 사진만). 다른 팀원의 인증은 승인된 것만, 본인 인증은 판정 상태와 함께 보인다.
 *
 * <p>조회는 참여 중이거나 끝까지 참여한 사람만 할 수 있다. 퇴장자의 열람 범위는 [OPEN, O-16]이라 막아 둔다. 사진 서명 주소는 권한을 확인한
 * 뒤에만 발급한다.
 *
 * <p>TODO(D-35): 경로·날짜 단위 조회·조회 가능 상태·본인 인증 노출은 제안값이다.
 */
@Service
public class ReadCertificationFeedService {

	private static final Logger log = LoggerFactory.getLogger(ReadCertificationFeedService.class);
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	static final Set<String> VIEWABLE_STATUSES = Set.of("ACTIVE", "COMPLETED", "EARLY_ENDED");

	private final CertificationFeedTargets targets;
	private final CertificationFeedStore store;
	private final CertificationImageUrls imageUrls;
	private final Clock clock;

	public ReadCertificationFeedService(CertificationFeedTargets targets, CertificationFeedStore store,
			CertificationImageUrls imageUrls, Clock clock) {
		this.targets = targets;
		this.store = store;
		this.imageUrls = imageUrls;
		this.clock = clock;
	}

	/** @param date null이면 오늘(KST) */
	public CertificationFeed read(long userId, long challengeId, LocalDate date) {
		if (!targets.challengeExists(challengeId)) {
			throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
		}
		Viewer viewer = targets.findViewer(challengeId, userId)
				.filter(found -> VIEWABLE_STATUSES.contains(found.status()))
				.orElseThrow(() -> {
					log.warn("인증 피드 조회 거절: 참여 확정자가 아니다. challengeId={} userId={}", challengeId, userId);
					return new BusinessException(CertificationErrorCode.FEED_NOT_MEMBER);
				});
		LocalDate certDate = date == null ? LocalDate.now(clock.withZone(KST)) : date;

		List<FeedRow> rows = store.find(challengeId, certDate, viewer.participationId(), userId);
		Map<Long, Submitter> submitters = targets.findSubmitters(
				rows.stream().map(FeedRow::participationId).distinct().toList());
		Map<String, String> urls = imageUrls.resolveByKey(
				rows.stream().map(FeedRow::imageKey).filter(Objects::nonNull).toList());

		List<CertificationFeed.Item> items = rows.stream()
				.filter(row -> submitters.containsKey(row.participationId()))
				.map(row -> {
					Submitter submitter = submitters.get(row.participationId());
					return new CertificationFeed.Item(row.certificationId(), submitter.userId(), submitter.nickname(),
							submitter.profileImageUrl(), submitter.role(), row.status(), row.submittedAt(),
							row.imageKey() == null ? null : urls.get(row.imageKey()),
							row.participationId() == viewer.participationId(), row.likeCount(), row.liked());
				})
				.toList();
		return new CertificationFeed(certDate, items);
	}
}
