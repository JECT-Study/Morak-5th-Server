package com.morak.feature.certification.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.morak.feature.certification.application.port.out.CertificationJudgeNotices;
import com.morak.feature.certification.application.port.out.CertificationJudgeStore;
import com.morak.feature.certification.application.port.out.CertificationJudgeStore.ApprovedDay;
import com.morak.feature.certification.application.port.out.CertificationJudgeTargets;
import com.morak.feature.certification.application.port.out.CertificationJudgeTargets.Challenge;
import com.morak.feature.certification.application.port.out.CertificationJudgeTargets.Member;
import com.morak.feature.certification.domain.vo.CertificationJudge;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

/** 위키 §4.4 정오 자동 승인과 경고, §7 "정오까지 담당자가 4건 미처리". */
class AutoApproveCertificationsServiceTest {

	private static final long CHALLENGE = 10L;
	private static final Instant NOON = Instant.parse("2026-11-25T03:00:00Z"); // 11/25 12:00 KST
	private static final LocalDate DAY = LocalDate.of(2026, 11, 24);

	private static final Member HOST = new Member(100L, 1L, "HOST", "ACTIVE");
	private static final Member DEPUTY = new Member(101L, 2L, "DEPUTY", "ACTIVE");
	private static final Member M1 = new Member(102L, 3L, "MEMBER", "ACTIVE");
	private static final Member M2 = new Member(103L, 4L, "MEMBER", "ACTIVE");
	private static final Member M3 = new Member(104L, 5L, "MEMBER", "ACTIVE");

	private final CertificationJudgeStore store = mock(CertificationJudgeStore.class);
	private final CertificationJudgeTargets targets = mock(CertificationJudgeTargets.class);
	private final CertificationJudgeNotices notices = mock(CertificationJudgeNotices.class);
	private final AutoApproveCertificationsService service = new AutoApproveCertificationsService(store, targets,
			notices, mock(PlatformTransactionManager.class), Clock.fixed(NOON, ZoneOffset.UTC));

	private List<Member> members = List.of(HOST, DEPUTY, M1, M2, M3);
	/** 이전 날짜까지 이미 자동 승인된 인증 */
	private final List<ApprovedDay> before = new ArrayList<>();

	@BeforeEach
	void setUp() {
		when(store.findChallengesWithOverdue(NOON)).thenReturn(List.of(CHALLENGE));
		when(targets.findChallenge(CHALLENGE)).thenReturn(Optional.of(new Challenge("테스트 챌린지",
				LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 26), 0)));
		when(targets.findMembers(CHALLENGE)).thenAnswer(invocation -> members);
		when(targets.findParticipants(any())).thenAnswer(invocation -> {
			Collection<Long> ids = invocation.getArgument(0);
			return members.stream().filter(m -> ids.contains(m.participationId()))
					.collect(Collectors.toMap(Member::participationId, m -> m));
		});
		when(notices.judgeOverdue(anyLong(), anyLong(), anyString(), any(), any(), anyInt())).thenReturn(true);
	}

	@Test
	void hostMissingFourCertificationsIsWarnedOnce() {
		approve(List.of(day(DEPUTY), day(M1), day(M2), day(M3)));

		assertEquals(4, service.run());

		verify(notices).judgeOverdue(HOST.userId(), CHALLENGE, "테스트 챌린지", CertificationJudge.HOST, DAY, 1);
		verify(notices, never()).judgeOverdue(anyLong(), anyLong(), anyString(),
				org.mockito.ArgumentMatchers.eq(CertificationJudge.DEPUTY), any(), anyInt());
		verify(notices, never()).judgeOverdueToTeam(anyLong(), any(), anyInt());
	}

	@Test
	void hostAndDeputyAreCountedSeparately() {
		approve(List.of(day(HOST), day(M1), day(M2), day(M3), day(DEPUTY)));

		service.run();

		verify(notices).judgeOverdue(HOST.userId(), CHALLENGE, "테스트 챌린지", CertificationJudge.HOST, DAY, 1);
		verify(notices).judgeOverdue(DEPUTY.userId(), CHALLENGE, "테스트 챌린지", CertificationJudge.DEPUTY, DAY, 1);
	}

	@Test
	void secondWarningIsPostedToTeamChat() {
		// 이틀 전에도 방장이 M1 인증을 처리하지 않았다. 부방장 경고 1회는 방장 횟수에 더하지 않는다.
		before.add(new ApprovedDay(M1.participationId(), DAY.minusDays(1)));
		before.add(new ApprovedDay(HOST.participationId(), DAY.minusDays(2)));
		approve(List.of(day(M2)));

		service.run();

		verify(notices).judgeOverdue(HOST.userId(), CHALLENGE, "테스트 챌린지", CertificationJudge.HOST, DAY, 2);
		verify(notices).judgeOverdueToTeam(CHALLENGE, CertificationJudge.HOST, 2);
	}

	@Test
	void missedDaysAreWarnedOnePerDateInOrder() {
		approve(List.of(new ApprovedDay(M1.participationId(), DAY.minusDays(1)), day(M1)));

		service.run();

		verify(notices).judgeOverdue(HOST.userId(), CHALLENGE, "테스트 챌린지", CertificationJudge.HOST, DAY.minusDays(1), 1);
		verify(notices).judgeOverdue(HOST.userId(), CHALLENGE, "테스트 챌린지", CertificationJudge.HOST, DAY, 2);
		verify(notices).judgeOverdueToTeam(CHALLENGE, CertificationJudge.HOST, 2);
	}

	@Test
	void alreadySentWarningDoesNotRepostToChat() {
		before.add(new ApprovedDay(M1.participationId(), DAY.minusDays(1)));
		approve(List.of(day(M2)));
		when(notices.judgeOverdue(anyLong(), anyLong(), anyString(), any(), any(), anyInt())).thenReturn(false);

		service.run();

		verify(notices, never()).judgeOverdueToTeam(anyLong(), any(), anyInt());
	}

	@Test
	void rerunWithNothingLeftSendsNothing() {
		when(store.autoApprove(CHALLENGE, NOON)).thenReturn(List.of());

		assertEquals(0, service.run());

		verifyNoInteractions(notices);
	}

	@Test
	void skipsWarningWhenNoDeputyIsLeft() {
		members = List.of(HOST, M1, new Member(101L, 2L, "DEPUTY", "EXPELLED"));
		approve(List.of(day(HOST)));

		service.run();

		verify(notices, never()).judgeOverdue(anyLong(), anyLong(), anyString(), any(), any(), anyInt());
	}

	@Test
	void oneChallengeFailingDoesNotStopOthers() {
		when(store.findChallengesWithOverdue(NOON)).thenReturn(List.of(CHALLENGE - 1, CHALLENGE));
		when(store.autoApprove(CHALLENGE - 1, NOON)).thenThrow(new IllegalStateException("boom"));
		approve(List.of(day(M1)));

		assertEquals(1, service.run());
	}

	private void approve(List<ApprovedDay> rows) {
		when(store.autoApprove(CHALLENGE, NOON)).thenReturn(rows);
		List<ApprovedDay> all = new ArrayList<>(before);
		all.addAll(rows);
		when(store.findAutoApproved(CHALLENGE)).thenReturn(all);
	}

	private static ApprovedDay day(Member submitter) {
		return new ApprovedDay(submitter.participationId(), DAY);
	}
}
