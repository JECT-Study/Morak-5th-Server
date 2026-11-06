package com.morak.feature.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morak.common.exception.BusinessException;
import com.morak.feature.certification.application.query.CertificationQuery;
import com.morak.feature.chat.application.dto.ChatMessageResult;
import com.morak.feature.chat.application.service.ChatMessageService;
import com.morak.feature.chat.domain.entity.ChatMessage;
import com.morak.feature.chat.domain.entity.ChatMessageType;
import com.morak.feature.chat.domain.exception.ChatErrorCode;
import com.morak.feature.chat.domain.repository.ChatMessageRepository;
import com.morak.feature.participation.application.query.ParticipationQuery;
import com.morak.feature.user.application.query.UserQuery;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 PostgreSQL(Testcontainers)로 채팅 영속화와 다른 모듈 조회(JDBC)를 확인한다. Docker가 실행 중이어야 한다.
 * 마이그레이션이 적용되고 엔티티 매핑 검증(ddl-auto=validate)을 통과하는지도 함께 확인된다.
 */
@SpringBootTest(properties = "morak.auth.jwt.secret=integration-test-secret-integration-test-secret")
class ChatPersistenceIntegrationTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 6, 0);

	@TestConfiguration(proxyBeanMethods = false)
	static class Containers {

		@Bean
		@ServiceConnection
		PostgreSQLContainer postgres() {
			return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
		}
	}

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private ChatMessageRepository messageRepository;

	@Autowired
	private ChatMessageService chatMessageService;

	@Autowired
	private ParticipationQuery participationQuery;

	@Autowired
	private CertificationQuery certificationQuery;

	@Autowired
	private UserQuery userQuery;

	private long challengeId;
	private long hostUserId;
	private long hostParticipationId;
	private long friendUserId;
	private long friendParticipationId;

	@BeforeEach
	void setUp() {
		for (String table : new String[] {"chat_message", "certification", "participation", "challenge", "users"}) {
			jdbc.sql("DELETE FROM morak." + table).update();
		}
		hostUserId = insertUser("모락");
		friendUserId = insertUser("친구");
		challengeId = insertChallenge(hostUserId, LocalDate.now().plusDays(7));
		hostParticipationId = insertParticipation(challengeId, hostUserId, "HOST", "ACTIVE");
		friendParticipationId = insertParticipation(challengeId, friendUserId, "MEMBER", "ACTIVE");
	}

	@Test
	void savedMessageIsFoundByClientMessageId() {
		UUID clientMessageId = UUID.randomUUID();
		messageRepository.save(ChatMessage.user(challengeId, hostParticipationId, clientMessageId, "안녕", null, NOW));

		ChatMessage found = messageRepository
				.findByParticipationIdAndClientMessageId(hostParticipationId, clientMessageId)
				.orElseThrow();

		assertEquals("안녕", found.getContent());
		assertEquals(ChatMessageType.USER, found.getType());
		assertEquals(NOW, found.getCreatedAt());
		assertTrue(messageRepository.findByParticipationIdAndClientMessageId(friendParticipationId, clientMessageId).isEmpty());
	}

	@Test
	void sameParticipantAndClientMessageIdViolatesUniqueIndex() {
		UUID clientMessageId = UUID.randomUUID();
		messageRepository.save(ChatMessage.user(challengeId, hostParticipationId, clientMessageId, "처음", null, NOW));

		assertThrows(DataIntegrityViolationException.class, () -> messageRepository.save(
				ChatMessage.user(challengeId, hostParticipationId, clientMessageId, "중복", null, NOW)));
		messageRepository.save(ChatMessage.user(challengeId, friendParticipationId, clientMessageId, "다른 참가자", null, NOW));
	}

	@Test
	void systemMessagesWithoutParticipationAreAllowedRepeatedly() {
		messageRepository.save(ChatMessage.system(challengeId, "경고 누적 안내", NOW));
		messageRepository.save(ChatMessage.system(challengeId, "경고 누적 안내", NOW));

		List<ChatMessage> latest = messageRepository.findLatest(challengeId, 10);

		assertEquals(2, latest.size());
		assertEquals(ChatMessageType.SYSTEM, latest.get(0).getType());
		assertNull(latest.get(0).getParticipationId());
	}

	@Test
	void participationQueryReturnsOnlyChatReadableStatuses() {
		long noticedUser = insertUser("퇴장예정");
		long completedUser = insertUser("완주자");
		long pendingUser = insertUser("대기자");
		long expelledUser = insertUser("퇴장자");
		insertParticipation(challengeId, noticedUser, "MEMBER", "EXPEL_NOTICED");
		insertParticipation(challengeId, completedUser, "MEMBER", "COMPLETED");
		insertParticipation(challengeId, pendingUser, "MEMBER", "PENDING");
		insertParticipation(challengeId, expelledUser, "MEMBER", "EXPELLED");

		assertEquals("ACTIVE", participationQuery.findJoinedParticipant(challengeId, hostUserId).orElseThrow().status());
		assertEquals("EXPEL_NOTICED", participationQuery.findJoinedParticipant(challengeId, noticedUser).orElseThrow().status());
		assertEquals("COMPLETED", participationQuery.findJoinedParticipant(challengeId, completedUser).orElseThrow().status());
		assertTrue(participationQuery.findJoinedParticipant(challengeId, pendingUser).isEmpty());
		assertTrue(participationQuery.findJoinedParticipant(challengeId, expelledUser).isEmpty());

		var byId = participationQuery.findParticipantsByIds(List.of(hostParticipationId, friendParticipationId, -1L));
		assertEquals(2, byId.size());
		assertEquals(hostUserId, byId.get(hostParticipationId).userId());
		assertEquals("HOST", byId.get(hostParticipationId).role());
	}

	@Test
	void certificationQueryOnlyReturnsCertificationsOfTheSameChallenge() {
		long certificationId = insertCertification(challengeId, friendParticipationId, LocalDate.of(2026, 11, 4));
		long otherChallenge = insertChallenge(hostUserId, LocalDate.now().plusDays(7));

		var found = certificationQuery.findInChallenge(challengeId, List.of(certificationId, -1L));
		assertEquals(1, found.size());
		assertEquals(friendParticipationId, found.get(certificationId).participationId());
		assertEquals(LocalDate.of(2026, 11, 4), found.get(certificationId).certDate());
		assertTrue(certificationQuery.findInChallenge(otherChallenge, List.of(certificationId)).isEmpty());
	}

	@Test
	void userQueryReturnsNicknames() {
		var profiles = userQuery.findProfilesByIds(List.of(hostUserId, friendUserId, -1L));

		assertEquals(2, profiles.size());
		assertEquals("모락", profiles.get(hostUserId).nickname());
	}

	@Test
	void sendAndListWorkThroughRealAdaptersIncludingReplyAndResend() {
		long certificationId = insertCertification(challengeId, friendParticipationId, LocalDate.of(2026, 11, 4));
		UUID clientMessageId = UUID.randomUUID();

		ChatMessageResult sent = chatMessageService.send(challengeId, hostUserId, clientMessageId, "멋져요!", certificationId);
		ChatMessageResult resent = chatMessageService.send(challengeId, hostUserId, clientMessageId, "멋져요!", certificationId);
		ChatMessageResult plain = chatMessageService.send(challengeId, friendUserId, UUID.randomUUID(), "고마워요", null);

		assertEquals(sent.id(), resent.id());
		assertEquals("모락", sent.sender().nickname());
		assertEquals("HOST", sent.sender().role());
		assertEquals(hostUserId, sent.sender().userId());
		assertEquals(certificationId, sent.reply().certificationId());
		assertEquals("친구", sent.reply().authorNickname());
		assertEquals(LocalDate.of(2026, 11, 4), sent.reply().certDate());

		List<ChatMessageResult> listed = chatMessageService.list(challengeId, friendUserId, null, null, null);
		assertEquals(List.of(sent.id(), plain.id()), listed.stream().map(ChatMessageResult::id).toList());
		assertEquals(sent.createdAt(), listed.get(0).createdAt());
		assertFalse(listed.get(1).createdAt().isBefore(listed.get(0).createdAt()));
	}

	@Test
	void replyToCertificationOfAnotherChallengeIsRejected() {
		long otherChallenge = insertChallenge(hostUserId, LocalDate.now().plusDays(7));
		long otherParticipation = insertParticipation(otherChallenge, hostUserId, "HOST", "ACTIVE");
		long foreignCertification = insertCertification(otherChallenge, otherParticipation, LocalDate.of(2026, 11, 4));

		BusinessException exception = assertThrows(BusinessException.class, () -> chatMessageService
				.send(challengeId, hostUserId, UUID.randomUUID(), "남의 방 인증", foreignCertification));

		assertEquals(ChatErrorCode.INVALID_REPLY_TARGET, exception.getErrorCode());
	}

	private long insertUser(String nickname) {
		return jdbc.sql("""
				INSERT INTO morak.users (nickname, status, created_at, updated_at)
				VALUES (:nickname, 'ACTIVE', now(), now()) RETURNING id
				""")
				.param("nickname", nickname)
				.query(Long.class)
				.single();
	}

	private long insertChallenge(long ownerUserId, LocalDate endDate) {
		return jdbc.sql("""
				INSERT INTO morak.challenge (owner_user_id, title, category, invite_code, start_date, end_date,
											duration_weeks, capacity, created_at, updated_at)
				VALUES (:owner, '테스트 챌린지', 'STUDY', :inviteCode, :start, :end, 2, 5, now(), now()) RETURNING id
				""")
				.param("owner", ownerUserId)
				.param("inviteCode", UUID.randomUUID().toString().substring(0, 6).toUpperCase())
				.param("start", endDate.minusDays(14))
				.param("end", endDate)
				.query(Long.class)
				.single();
	}

	private long insertParticipation(long challenge, long userId, String role, String status) {
		return jdbc.sql("""
				INSERT INTO morak.participation (challenge_id, user_id, role, status, applied_at)
				VALUES (:challenge, :user, :role, :status, now()) RETURNING id
				""")
				.param("challenge", challenge)
				.param("user", userId)
				.param("role", role)
				.param("status", status)
				.query(Long.class)
				.single();
	}

	private long insertCertification(long challenge, long participationId, LocalDate certDate) {
		return jdbc.sql("""
				INSERT INTO morak.certification (challenge_id, participation_id, cert_date, submitted_at, updated_at)
				VALUES (:challenge, :participation, :certDate, now(), now()) RETURNING id
				""")
				.param("challenge", challenge)
				.param("participation", participationId)
				.param("certDate", certDate)
				.query(Long.class)
				.single();
	}
}
