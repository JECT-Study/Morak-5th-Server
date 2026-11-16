package com.morak.feature.challenge.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.feature.challenge.application.dto.CreateChallengeCommand;
import com.morak.feature.challenge.application.port.out.ChallengeCoverStorage;
import com.morak.feature.challenge.application.service.CreateChallengeService.CreatedChallenge;
import com.morak.feature.challenge.domain.exception.ChallengeErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChallengeCoverImages {

	private static final Logger log = LoggerFactory.getLogger(ChallengeCoverImages.class);
	public static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
	private static final long MAX_IMAGE_PIXELS = 25_000_000;
	private static final String WEBP = "image/webp";

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
		validateImage(content, contentType);
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

	private static void validateImage(byte[] content, String contentType) {
		if (WEBP.equals(contentType)) {
			validateWebpChunks(content);
		}
		Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType(contentType);
		if (!readers.hasNext()) {
			throw new BusinessException(ChallengeErrorCode.COVER_STORAGE_UNAVAILABLE);
		}
		ImageReader reader = readers.next();
		try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
			reader.setInput(input);
			reader.addIIOReadWarningListener((source, warning) -> {
				if (!WEBP.equals(contentType) || !warning.startsWith("Ignoring unexpected chunk: ")) {
					throw new BusinessException(ChallengeErrorCode.INVALID_COVER_IMAGE);
				}
			});
			if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_IMAGE_PIXELS) {
				throw new BusinessException(ChallengeErrorCode.INVALID_COVER_IMAGE);
			}
			reader.read(0);
		} catch (IOException exception) {
			throw new BusinessException(ChallengeErrorCode.INVALID_COVER_IMAGE);
		} finally {
			reader.dispose();
		}
	}

	/** VP8X reports the canvas size. The decoder can allocate using the bitstream dimensions instead. */
	private static void validateWebpChunks(byte[] content) {
		ByteBuffer data = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);
		if (Integer.toUnsignedLong(data.getInt(4)) != content.length - 8L) {
			throw new BusinessException(ChallengeErrorCode.INVALID_COVER_IMAGE);
		}
		validateWebpChunks(content, data, 12, content.length, true);
	}

	private static void validateWebpChunks(byte[] content, ByteBuffer data, int offset, int end, boolean topLevel) {
		while (offset < end) {
			if (end - offset < 8) {
				throw new BusinessException(ChallengeErrorCode.INVALID_COVER_IMAGE);
			}
			String type = ascii(content, offset, 4);
			long size = Integer.toUnsignedLong(data.getInt(offset + 4));
			int payload = offset + 8;
			long paddedSize = size + (size & 1);
			if (paddedSize > end - payload) {
				throw new BusinessException(ChallengeErrorCode.INVALID_COVER_IMAGE);
			}
			switch (type) {
				case "VP8 " -> {
					if (size < 10) {
						throw new BusinessException(ChallengeErrorCode.INVALID_COVER_IMAGE);
					}
					checkWebpSize(data.getShort(payload + 6) & 0x3fff, data.getShort(payload + 8) & 0x3fff);
				}
				case "VP8L" -> {
					if (size < 5) {
						throw new BusinessException(ChallengeErrorCode.INVALID_COVER_IMAGE);
					}
					int dimensions = data.getInt(payload + 1);
					checkWebpSize((dimensions & 0x3fff) + 1, ((dimensions >>> 14) & 0x3fff) + 1);
				}
				case "ANMF" -> {
					if (!topLevel || size < 16) {
						throw new BusinessException(ChallengeErrorCode.INVALID_COVER_IMAGE);
					}
					int width = (data.getInt(payload + 6) & 0xffffff) + 1;
					int height = (data.getInt(payload + 9) & 0xffffff) + 1;
					checkWebpSize(width, height);
					validateWebpChunks(content, data, payload + 16, payload + (int) size, false);
				}
				default -> {
					// The decoder handles other chunks after the frame size checks.
				}
			}
			offset = payload + (int) paddedSize;
		}
	}

	private static void checkWebpSize(int width, int height) {
		if (width == 0 || height == 0 || (long) width * height > MAX_IMAGE_PIXELS) {
			throw new BusinessException(ChallengeErrorCode.INVALID_COVER_IMAGE);
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
			return WEBP;
		}
		throw new BusinessException(ChallengeErrorCode.INVALID_COVER_IMAGE);
	}

	private static String ascii(byte[] content, int offset, int length) {
		return new String(content, offset, length, StandardCharsets.US_ASCII);
	}
}
