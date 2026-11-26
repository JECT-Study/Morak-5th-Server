package com.morak.feature.certification.application.service;

import com.morak.feature.certification.application.port.out.CertificationExpelNotices;
import com.morak.feature.certification.application.port.out.CertificationExpelStore;
import com.morak.feature.certification.application.port.out.CertificationExpelStore.FailCount;
import com.morak.feature.certification.application.port.out.CertificationExpelTargets;
import com.morak.feature.certification.application.port.out.CertificationExpelTargets.Expelled;
import com.morak.feature.certification.application.port.out.CertificationExpelTargets.Outcome;
import com.morak.feature.certification.domain.vo.ExpelThreshold;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 매일 12:05 KST 퇴장 판정·실행(위키 §4.4 퇴장·조기 종료, §4.9 ⑥). 포인트 없는 챌린지에서 전날까지의 실패(미제출·거절·마감 후 제출)가
 * {@link ExpelThreshold} 이상인 ACTIVE 참가자를 바로 퇴장시키고 알림 #10을 보낸다. 퇴장 후 공개 챌린지가 2명 이하로 남으면
 * 같은 트랜잭션에서 조기 종료하고 남은 사람에게 알림 #11을 보낸다. 정오 자동 승인(⑤)이 끝난 뒤 돌아야 한다.
 *
 * <p>챌린지마다 한 트랜잭션이라 한 건이 실패해도 그 챌린지만 롤백된다. 이미 퇴장한 사람은 ACTIVE가 아니라 다시 세지 않으므로 여러 번 돌려도
 * 결과가 같다.
 *
 * <p>TODO(D-46): 진행 기간(오늘 ≤ 종료일)에만 판정하므로, 종료일 다음 날 정오에 마지막 날 실패로 기준에 닿는 사람은 퇴장시키지 않는다.
 */
@Service
public class ExpelFailedParticipantsService {

	private static final Logger log = LoggerFactory.getLogger(ExpelFailedParticipantsService.class);
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	/** ⑥은 포인트 없는 챌린지만 다룬다. 포인트 챌린지는 운영진 검수 완료(⑦)에서 판정한다. */
	private static final int NO_POINT = 0;

	private final CertificationExpelStore store;
	private final CertificationExpelTargets targets;
	private final CertificationExpelNotices notices;
	private final TransactionTemplate transaction;
	private final Clock clock;

	public ExpelFailedParticipantsService(CertificationExpelStore store, CertificationExpelTargets targets,
			CertificationExpelNotices notices, PlatformTransactionManager transactionManager, Clock clock) {
		this.store = store;
		this.targets = targets;
		this.notices = notices;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/** @return 퇴장시킨 참가자 수 */
	public int run() {
		Instant now = clock.instant();
		LocalDate today = LocalDate.ofInstant(now, KST);
		Map<Long, List<FailCount>> byChallenge = store.findFailCounts(today).stream()
				.filter(row -> row.failCount() >= ExpelThreshold.of(row.startDate(), row.endDate()))
				.collect(Collectors.groupingBy(FailCount::challengeId, LinkedHashMap::new, Collectors.toList()));
		int expelled = 0;
		for (List<FailCount> rows : byChallenge.values()) {
			try {
				expelled += transaction.execute(status -> process(rows, today, now));
			} catch (RuntimeException exception) {
				log.error("퇴장 판정 실패: challengeId={}", rows.get(0).challengeId(), exception);
			}
		}
		return expelled;
	}

	private int process(List<FailCount> rows, LocalDate today, Instant now) {
		FailCount first = rows.get(0);
		Map<Long, FailCount> byParticipation = rows.stream()
				.collect(Collectors.toMap(FailCount::participationId, Function.identity()));
		Outcome outcome = targets.expel(first.challengeId(), byParticipation.keySet(), today, now);
		for (Expelled member : outcome.expelled()) {
			notices.expelled(member.userId(), first.challengeId(), member.participationId(), first.title(),
					byParticipation.get(member.participationId()).failCount(), NO_POINT);
		}
		for (long userId : outcome.earlyEndedUserIds()) {
			notices.earlyEnded(userId, first.challengeId(), first.title(), NO_POINT);
		}
		log.info("퇴장 판정: challengeId={}, expelled={}, earlyEnded={}", first.challengeId(), outcome.expelled().size(),
				!outcome.earlyEndedUserIds().isEmpty());
		return outcome.expelled().size();
	}
}
