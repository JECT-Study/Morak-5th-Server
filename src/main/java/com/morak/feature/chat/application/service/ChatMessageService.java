package com.morak.feature.chat.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.chat.application.dto.ChatMessageResult;
import com.morak.feature.chat.application.port.out.ChatParticipantReader;
import com.morak.feature.chat.application.port.out.ChatParticipantReader.ChatParticipant;
import com.morak.feature.chat.domain.entity.ChatMessage;
import com.morak.feature.chat.domain.exception.ChatErrorCode;
import com.morak.feature.chat.domain.repository.ChatMessageRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatMessageService {

	public static final int DEFAULT_LIMIT = 30;
	public static final int MAX_LIMIT = 100;
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final ChatMessageRepository messageRepository;
	private final ChatParticipantReader participantReader;
	private final Clock clock;

	public ChatMessageService(
			ChatMessageRepository messageRepository, ChatParticipantReader participantReader, Clock clock) {
		this.messageRepository = messageRepository;
		this.participantReader = participantReader;
		this.clock = clock;
	}

	/** 메시지 전송. 참여중(ACTIVE)이고 챌린지 종료일 다음 날 00:00(KST) 전일 때만 가능. */
	@Transactional
	public ChatMessageResult send(Long challengeId, Long userId, String content) {
		ChatParticipant participant = requireParticipant(challengeId, userId);
		if (!participant.isActive()) {
			throw new BusinessException(ChatErrorCode.NOT_PARTICIPANT);
		}
		if (isClosed(challengeId)) {
			throw new BusinessException(ChatErrorCode.CHAT_CLOSED);
		}
		if (content == null || content.isBlank() || content.length() > ChatMessage.MAX_CONTENT_LENGTH) {
			throw new BusinessException(ChatErrorCode.INVALID_MESSAGE);
		}
		ChatMessage saved = messageRepository.save(new ChatMessage(
				challengeId, participant.participationId(), content, LocalDateTime.now(clock.withZone(KST))));
		return ChatMessageResult.of(saved, participant.userId());
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
		return toResults(messages);
	}

	/** 메시지 삭제 (뼈대만 존재. 작성자·방장 권한 검사와 soft delete는 추후 구현). */
	@Transactional
	public void delete(Long messageId, Long userId) {
		throw new BusinessException(ChatErrorCode.NOT_IMPLEMENTED);
	}

	private ChatParticipant requireParticipant(Long challengeId, Long userId) {
		return participantReader.findChatParticipant(challengeId, userId)
				.orElseThrow(() -> new BusinessException(ChatErrorCode.NOT_PARTICIPANT));
	}

	private boolean isClosed(Long challengeId) {
		LocalDate endDate = participantReader.findChallengeEndDate(challengeId)
				.orElseThrow(() -> new BusinessException(ChatErrorCode.CHALLENGE_NOT_FOUND));
		return !clock.instant().isBefore(endDate.plusDays(1).atStartOfDay(KST).toInstant());
	}

	private List<ChatMessageResult> toResults(List<ChatMessage> messages) {
		Set<Long> participationIds = messages.stream().map(ChatMessage::getParticipationId).collect(Collectors.toSet());
		Map<Long, Long> userIds = participantReader.findUserIdsByParticipationIds(participationIds);
		return messages.stream()
				.map(message -> ChatMessageResult.of(message, userIds.get(message.getParticipationId())))
				.toList();
	}
}
