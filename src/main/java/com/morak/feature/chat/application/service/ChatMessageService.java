package com.morak.feature.chat.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.chat.application.dto.ChatMessageResult;
import com.morak.feature.chat.application.dto.ChatMessageResult.Reply;
import com.morak.feature.chat.application.dto.ChatMessageResult.Sender;
import com.morak.feature.chat.application.port.out.ChatCertificationReader;
import com.morak.feature.chat.application.port.out.ChatCertificationReader.ReplyTarget;
import com.morak.feature.chat.application.port.out.ChatParticipantReader;
import com.morak.feature.chat.application.port.out.ChatParticipantReader.ChatParticipant;
import com.morak.feature.chat.application.port.out.ChatParticipantReader.ChatSender;
import com.morak.feature.chat.domain.entity.ChatMessage;
import com.morak.feature.chat.domain.exception.ChatErrorCode;
import com.morak.feature.chat.domain.repository.ChatMessageRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatMessageService {

	public static final int DEFAULT_LIMIT = 30;
	public static final int MAX_LIMIT = 100;
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final ChatMessageRepository messageRepository;
	private final ChatParticipantReader participantReader;
	private final ChatCertificationReader certificationReader;
	private final Clock clock;

	public ChatMessageService(
			ChatMessageRepository messageRepository,
			ChatParticipantReader participantReader,
			ChatCertificationReader certificationReader,
			Clock clock) {
		this.messageRepository = messageRepository;
		this.participantReader = participantReader;
		this.certificationReader = certificationReader;
		this.clock = clock;
	}

	/**
	 * 메시지 전송. 참여중(ACTIVE)이고 챌린지 종료일 다음 날 00:00(KST) 전일 때만 가능.
	 * 같은 참가자가 같은 clientMessageId로 다시 보내면 새로 저장하지 않고 이미 저장된 메시지를 돌려준다(멱등).
	 *
	 * 퇴장 공지~퇴장(EXPEL_NOTICED)은 읽기만 허용한다(백엔드 위키 D-11).
	 *
	 * TODO: 위키의 종료 기준은 "최종 결과 확정일 24:00"이지만 정산(finalized_at)이 생기기 전까지는 챌린지 종료일을 기준으로 한다.
	 */
	@Transactional
	public ChatMessageResult send(
			Long challengeId, Long userId, UUID clientMessageId, String content, Long replyCertificationId) {
		ChatParticipant participant = requireParticipant(challengeId, userId);
		if (!participant.isActive()) {
			throw new BusinessException(ChatErrorCode.SEND_FORBIDDEN);
		}
		if (isClosed(challengeId)) {
			throw new BusinessException(ChatErrorCode.CHAT_CLOSED);
		}
		if (clientMessageId == null) {
			throw new BusinessException(ChatErrorCode.INVALID_CLIENT_MESSAGE_ID);
		}
		if (content == null || content.isBlank() || content.length() > ChatMessage.MAX_CONTENT_LENGTH) {
			throw new BusinessException(ChatErrorCode.INVALID_MESSAGE);
		}

		Optional<ChatMessage> existing = messageRepository
				.findByParticipationIdAndClientMessageId(participant.participationId(), clientMessageId);
		ChatMessage message = existing.orElseGet(() -> {
			requireReplyTarget(challengeId, replyCertificationId);
			return messageRepository.save(ChatMessage.user(
					challengeId,
					participant.participationId(),
					clientMessageId,
					content,
					replyCertificationId,
					LocalDateTime.now(clock.withZone(ZoneOffset.UTC))));
		});
		return toResults(challengeId, List.of(message)).get(0);
	}

	/** 메시지 조회. 반환은 항상 id 오름차순. */
	@Transactional(readOnly = true)
	public List<ChatMessageResult> list(Long challengeId, Long userId, Long before, Long after, Integer limit) {
		if (before != null && after != null) {
			throw new BusinessException(ChatErrorCode.INVALID_CURSOR);
		}
		requireParticipant(challengeId, userId);
		int size = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));

		List<ChatMessage> messages;
		if (after != null) {
			messages = messageRepository.findAfter(challengeId, after, size);
		} else {
			messages = new ArrayList<>(before != null
					? messageRepository.findBefore(challengeId, before, size)
					: messageRepository.findLatest(challengeId, size));
			Collections.reverse(messages);
		}
		return toResults(challengeId, messages);
	}

	private ChatParticipant requireParticipant(Long challengeId, Long userId) {
		return participantReader.findChatParticipant(challengeId, userId)
				.orElseThrow(() -> new BusinessException(ChatErrorCode.NOT_PARTICIPANT));
	}

	private void requireReplyTarget(Long challengeId, Long replyCertificationId) {
		if (replyCertificationId == null) {
			return;
		}
		if (!certificationReader.findReplyTargets(challengeId, Set.of(replyCertificationId))
				.containsKey(replyCertificationId)) {
			throw new BusinessException(ChatErrorCode.INVALID_REPLY_TARGET);
		}
	}

	private boolean isClosed(Long challengeId) {
		LocalDate endDate = participantReader.findChallengeEndDate(challengeId)
				.orElseThrow(() -> new BusinessException(ChatErrorCode.CHALLENGE_NOT_FOUND));
		return !clock.instant().isBefore(endDate.plusDays(1).atStartOfDay(KST).toInstant());
	}

	private List<ChatMessageResult> toResults(Long challengeId, List<ChatMessage> messages) {
		Set<Long> replyIds = new HashSet<>();
		messages.stream()
				.map(ChatMessage::getReplyCertificationId)
				.filter(Objects::nonNull)
				.forEach(replyIds::add);
		Map<Long, ReplyTarget> replyTargets = certificationReader.findReplyTargets(challengeId, replyIds);

		Set<Long> participationIds = new HashSet<>();
		messages.stream()
				.map(ChatMessage::getParticipationId)
				.filter(Objects::nonNull)
				.forEach(participationIds::add);
		replyTargets.values().forEach(target -> participationIds.add(target.authorParticipationId()));
		Map<Long, ChatSender> senders = participantReader.findSenders(participationIds);

		return messages.stream()
				.map(message -> toResult(message, senders, replyTargets))
				.toList();
	}

	private ChatMessageResult toResult(
			ChatMessage message, Map<Long, ChatSender> senders, Map<Long, ReplyTarget> replyTargets) {
		return new ChatMessageResult(
				message.getId(),
				message.getType(),
				message.getClientMessageId(),
				toSender(senders.get(message.getParticipationId())),
				message.getContent(),
				toReply(message.getReplyCertificationId(), senders, replyTargets),
				message.getCreatedAt().toInstant(ZoneOffset.UTC));
	}

	private Sender toSender(ChatSender sender) {
		if (sender == null) {
			return null;
		}
		return new Sender(sender.userId(), sender.nickname(), sender.profileImageUrl(), sender.role());
	}

	private Reply toReply(Long certificationId, Map<Long, ChatSender> senders, Map<Long, ReplyTarget> replyTargets) {
		if (certificationId == null) {
			return null;
		}
		ReplyTarget target = replyTargets.get(certificationId);
		if (target == null) {
			return new Reply(certificationId, null, null);
		}
		ChatSender author = senders.get(target.authorParticipationId());
		return new Reply(certificationId, author == null ? null : author.nickname(), target.certDate());
	}
}
