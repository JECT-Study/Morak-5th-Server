package com.morak.feature.challenge.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.image.UploadedImages;
import com.morak.feature.challenge.application.dto.command.CreateChallengeCommand;
import com.morak.feature.challenge.application.dto.result.CreatedChallenge;
import com.morak.feature.challenge.application.port.out.ChallengeCoverStorage;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChallengeCoverImages {

	private static final Logger log = LoggerFactory.getLogger(ChallengeCoverImages.class);
	public static final int MAX_IMAGE_BYTES = UploadedImages.MAX_IMAGE_BYTES;

	private final Optional<ChallengeCoverStorage> storage;
	private final CreateChallengeService challenges;
	private final Clock clock;

	public ChallengeCoverImages(Optional<ChallengeCoverStorage> storage, CreateChallengeService challenges, Clock clock) {
		this.storage = storage;
		this.challenges = challenges;
		this.clock = clock;
	}

	public CreatedChallenge create(long ownerId, CreateChallengeCommand command, byte[] content) {
		if (content.length > MAX_IMAGE_BYTES) {
			throw new BusinessException(ChallengeErrorCode.COVER_IMAGE_TOO_LARGE);
		}
		String contentType = validateImage(content);
		command.validateStartDate(clock.instant());
		ChallengeCoverStorage bucket = storage.orElseThrow(
				() -> new BusinessException(ChallengeErrorCode.COVER_STORAGE_UNAVAILABLE));
		String key = "challenges/" + UUID.randomUUID();
		try {
			bucket.upload(key, content, contentType);
			return challenges.create(ownerId, command, key);
		} catch (RuntimeException exception) {
			try {
				bucket.delete(key);
			} catch (RuntimeException cleanupFailure) {
				log.warn("Could not remove unused challenge cover key={}", key);
			}
			throw exception;
		}
	}

	public Map<String, String> urls(List<String> keys) {
		if (keys.isEmpty() || storage.isEmpty()) {
			return Map.of();
		}
		try {
			return storage.get().signDownloads(keys);
		} catch (BusinessException exception) {
			log.warn("Could not sign challenge cover URLs");
			return Map.of();
		}
	}

	private static String validateImage(byte[] content) {
		try {
			return UploadedImages.validate(content);
		} catch (UploadedImages.InvalidImageException exception) {
			throw new BusinessException(ChallengeErrorCode.INVALID_COVER_IMAGE);
		} catch (IllegalStateException exception) {
			throw new BusinessException(ChallengeErrorCode.COVER_STORAGE_UNAVAILABLE);
		}
	}
}
