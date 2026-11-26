package com.morak.feature.certification.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.feature.certification.application.dto.result.JudgedCertification;
import com.morak.feature.certification.application.dto.result.PendingJudgment;
import com.morak.feature.certification.application.port.out.CertificationImageUrls;
import com.morak.feature.certification.application.port.out.CertificationJudgeNotices;
import com.morak.feature.certification.application.port.out.CertificationJudgeStore;
import com.morak.feature.certification.application.port.out.CertificationJudgeStore.JudgeRow;
import com.morak.feature.certification.application.port.out.CertificationJudgeStore.PendingRow;
import com.morak.feature.certification.application.port.out.CertificationJudgeTargets;
import com.morak.feature.certification.application.port.out.CertificationJudgeTargets.Challenge;
import com.morak.feature.certification.application.port.out.CertificationJudgeTargets.Member;
import com.morak.feature.certification.application.port.out.CertificationJudgeTargets.Profile;
import com.morak.feature.certification.domain.exception.CertificationErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 위키 §4.4 판정 담당 표의 경우별 판정 API. */
class JudgeCertificationServiceTest {

	private static final long CHALLENGE = 10L;
	private static final Instant NOW = Instant.parse("2026-11-25T01:00:00Z"); // 11/25 10:00 KST
	private static final Instant DUE = Instant.parse("2026-11-25T03:00:00Z"); // 11/25 12:00 KST
	private static final LocalDate DAY = LocalDate.of(2026, 11, 24);

	private static final Member HOST = new Member(100L, 1L, "HOST", "ACTIVE");
	private static final Member DEPUTY = new Member(101L, 2L, "DEPUTY", "ACTIVE");
	private static final Member MEMBER = new Member(102L, 3L, "MEMBER", "ACTIVE");
	private static final Member MEMBER2 = new Member(103L, 4L, "MEMBER", "ACTIVE");

	private final CertificationJudgeTargets targets = mock(CertificationJudgeTargets.class);
	private final CertificationJudgeStore store = mock(CertificationJudgeStore.class);
	private final CertificationJudgeNotices notices = mock(CertificationJudgeNotices.class);
	private final CertificationImageUrls urls = mock(CertificationImageUrls.class);
	private final JudgeCertificationService service = new JudgeCertificationService(targets, store, notices, urls,
			Clock.fixed(NOW, ZoneOffset.UTC));

	private final List<Member> members = List.of(HOST, DEPUTY, MEMBER, MEMBER2);

	@BeforeEach
	void setUp() {
		challenge(0);
		when(targets.findMembers(CHALLENGE)).thenReturn(members);
		when(targets.findParticipants(any())).thenAnswer(invocation -> {
			Collection<Long> ids = invocation.getArgument(0);
			return members.stream().filter(m -> ids.contains(m.participationId()))
					.collect(Collectors.toMap(Member::participationId, m -> m));
		});
		when(targets.findProfiles(any())).thenReturn(Map.of());
		when(urls.resolveByKey(any())).thenReturn(Map.of());
		when(store.findPending(CHALLENGE, NOW)).thenReturn(List.of(
				pending(1L, HOST), pending(2L, DEPUTY), pending(3L, MEMBER), pending(4L, MEMBER2)));
	}

	@Test
	void hostSeesEveryoneButHimselfIncludingDeputy() {
		assertEquals(List.of(2L, 3L, 4L), ids(service.pending(HOST.userId(), CHALLENGE)));
	}

	@Test
	void deputySeesOnlyHostCertification() {
		assertEquals(List.of(1L), ids(service.pending(DEPUTY.userId(), CHALLENGE)));
	}

	@Test
	void memberAndPointChallengeHaveNothingToJudge() {
		assertEquals(List.of(), service.pending(MEMBER.userId(), CHALLENGE));
		challenge(1000);
		assertEquals(List.of(), service.pending(HOST.userId(), CHALLENGE));
	}

	@Test
	void listFillsProfileAndSignedImage() {
		when(targets.findProfiles(List.of(HOST.userId()))).thenReturn(Map.of(HOST.userId(), new Profile("방장", "p")));
		when(urls.resolveByKey(List.of("k1"))).thenReturn(Map.of("k1", "https://signed/k1"));

		assertEquals(List.of(new PendingJudgment(1L, HOST.userId(), "방장", "p", "HOST", DAY, NOW.minusSeconds(60), DUE,
				"https://signed/k1")), service.pending(DEPUTY.userId(), CHALLENGE));
	}

	@Test
	void nonMemberCannotOpenTheList() {
		BusinessException exception = assertThrows(BusinessException.class, () -> service.pending(99L, CHALLENGE));
		assertEquals(CertificationErrorCode.NOT_JUDGE, exception.getErrorCode());
	}

	@Test
	void hostApprovesMemberAndDeputyApprovesHost() {
		certification(3L, MEMBER);
		certification(1L, HOST);
		when(store.judge(anyLong(), eq("APPROVED"), anyLong(), eq(null), eq(NOW))).thenReturn(true);

		assertEquals(new JudgedCertification(3L, "APPROVED"), service.approve(HOST.userId(), CHALLENGE, 3L));
		assertEquals(new JudgedCertification(1L, "APPROVED"), service.approve(DEPUTY.userId(), CHALLENGE, 1L));
		verify(store).judge(3L, "APPROVED", HOST.userId(), null, NOW);
		verify(store).judge(1L, "APPROVED", DEPUTY.userId(), null, NOW);
	}

	@Test
	void cannotJudgeOwnOrUnassignedCertification() {
		certification(1L, HOST);
		certification(2L, DEPUTY);
		certification(3L, MEMBER);

		assertError(CertificationErrorCode.NOT_JUDGE, () -> service.approve(HOST.userId(), CHALLENGE, 1L));
		assertError(CertificationErrorCode.NOT_JUDGE, () -> service.approve(DEPUTY.userId(), CHALLENGE, 2L));
		assertError(CertificationErrorCode.NOT_JUDGE, () -> service.approve(DEPUTY.userId(), CHALLENGE, 3L));
		assertError(CertificationErrorCode.NOT_JUDGE, () -> service.approve(MEMBER2.userId(), CHALLENGE, 3L));
		verify(store, never()).judge(anyLong(), anyString(), anyLong(), any(), any());
	}

	@Test
	void pointChallengeIsForAdmins() {
		challenge(1000);
		certification(3L, MEMBER);

		assertError(CertificationErrorCode.NOT_JUDGE, () -> service.approve(HOST.userId(), CHALLENGE, 3L));
	}

	@Test
	void certificationOfAnotherChallengeIsNotFound() {
		when(store.findById(3L)).thenReturn(Optional.of(new JudgeRow(3L, CHALLENGE + 1, MEMBER.participationId(), DAY,
				"PENDING", DUE)));

		assertError(CertificationErrorCode.JUDGMENT_NOT_FOUND, () -> service.approve(HOST.userId(), CHALLENGE, 3L));
	}

	@Test
	void missingChallengeIsNotFound() {
		when(targets.findChallenge(CHALLENGE)).thenReturn(Optional.empty());

		assertError(CommonErrorCode.RESOURCE_NOT_FOUND, () -> service.approve(HOST.userId(), CHALLENGE, 3L));
	}

	@Test
	void firstJudgmentWins() {
		certification(3L, MEMBER);
		when(store.judge(anyLong(), anyString(), anyLong(), any(), any())).thenReturn(false);
		when(store.findById(3L)).thenReturn(
				Optional.of(new JudgeRow(3L, CHALLENGE, MEMBER.participationId(), DAY, "PENDING", DUE)),
				Optional.of(new JudgeRow(3L, CHALLENGE, MEMBER.participationId(), DAY, "APPROVED", DUE)));

		assertError(CertificationErrorCode.ALREADY_JUDGED, () -> service.approve(HOST.userId(), CHALLENGE, 3L));
	}

	@Test
	void cannotJudgeAfterNoonDeadline() {
		when(store.findById(3L)).thenReturn(Optional.of(new JudgeRow(3L, CHALLENGE, MEMBER.participationId(), DAY,
				"PENDING", NOW)));
		when(store.judge(anyLong(), anyString(), anyLong(), any(), any())).thenReturn(false);

		assertError(CertificationErrorCode.JUDGE_DUE_PASSED, () -> service.approve(HOST.userId(), CHALLENGE, 3L));
	}

	@Test
	void rejectSendsNotificationWithRemainingFailures() {
		certification(3L, MEMBER);
		when(store.judge(3L, "REJECTED", HOST.userId(), "사진이 흐려요", NOW)).thenReturn(true);
		when(store.countFailures(MEMBER.participationId())).thenReturn(1);

		assertEquals(new JudgedCertification(3L, "REJECTED"),
				service.reject(HOST.userId(), CHALLENGE, 3L, "  사진이 흐려요 "));
		// 7일 챌린지의 퇴장 기준 2회 - 실패 1회
		verify(notices).rejected(MEMBER.userId(), CHALLENGE, 3L, "테스트 챌린지", DAY, 1);
	}

	@Test
	void rejectNeedsReason() {
		assertError(CertificationErrorCode.REJECT_REASON_REQUIRED, () -> service.reject(HOST.userId(), CHALLENGE, 3L, " "));
		verify(notices, never()).rejected(anyLong(), anyLong(), anyLong(), any(), any(), any(Integer.class));
	}

	private void challenge(int pointAmount) {
		when(targets.findChallenge(CHALLENGE)).thenReturn(Optional.of(new Challenge("테스트 챌린지",
				LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 26), pointAmount)));
	}

	private void certification(long id, Member submitter) {
		when(store.findById(id)).thenReturn(
				Optional.of(new JudgeRow(id, CHALLENGE, submitter.participationId(), DAY, "PENDING", DUE)));
	}

	private static PendingRow pending(long id, Member submitter) {
		return new PendingRow(id, submitter.participationId(), DAY, NOW.minusSeconds(60), DUE, "k" + id);
	}

	private static List<Long> ids(List<PendingJudgment> judgments) {
		return judgments.stream().map(PendingJudgment::certificationId).toList();
	}

	private static void assertError(Object code, Runnable action) {
		BusinessException exception = assertThrows(BusinessException.class, action::run);
		assertEquals(code, exception.getErrorCode());
	}
}
