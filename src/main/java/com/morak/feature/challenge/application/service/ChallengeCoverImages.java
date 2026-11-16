package com.morak.feature.challenge.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.application.dto.CreateChallengeCommand;
import com.morak.feature.challenge.application.port.out.ChallengeCoverStorage;
import com.morak.feature.challenge.application.service.CreateChallengeService.CreatedChallenge;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import java.nio.charset.StandardCharsets;
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
	public static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

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
		String contentType = imageType(content);
		command.validateStartDate(clock.instant());
		var bucket = storage.orElseThrow(() -> new BusinessException(ChallengeErrorCode.COVER_STORAGE_UNAVAILABLE));
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

	private static String imageType(byte[] content) {
		if (content.length >= 4 && content[0] == (byte) 0xff && content[1] == (byte) 0xd8
				&& content[2] == (byte) 0xff) {
			return "image/jpeg";
		}
		if (content.length >= 24 && content[0] == (byte) 0x89 && ascii(content, 1, 3).equals("PNG")
				&& content[4] == 13 && content[5] == 10 && content[6] == 26 && content[7] == 10
				&& ascii(content, 12, 4).equals("IHDR")) {
			return "image/png";
		}
		if (content.length >= 20 && ascii(content, 0, 4).equals("RIFF") && ascii(content, 8, 4).equals("WEBP")
				&& List.of("VP8 ", "VP8L", "VP8X").contains(ascii(content, 12, 4))) {
			return "image/webp";
		}
		throw new BusinessException(ChallengeErrorCode.INVALID_COVER_IMAGE);
	}

	private static String ascii(byte[] content, int offset, int length) {
		return new String(content, offset, length, StandardCharsets.US_ASCII);
	}
}
