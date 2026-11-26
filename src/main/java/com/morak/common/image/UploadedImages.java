package com.morak.common.image;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** 사용자가 올린 사진 검증. JPEG·PNG·WebP, 5MB 이하(D-17). 도메인마다 오류 코드가 달라 예외만 던진다. */
public final class UploadedImages {

	public static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
	private static final long MAX_IMAGE_PIXELS = 25_000_000;
	private static final String WEBP = "image/webp";

	private UploadedImages() {}

	/**
	 * 내용을 읽어 실제 형식을 확인하고 MIME 타입을 돌려준다. 크기 제한은 호출하는 쪽이 먼저 확인한다.
	 *
	 * @throws InvalidImageException 지원하지 않는 형식이거나 깨진 사진
	 * @throws IllegalStateException 서버에 해당 형식의 디코더가 없음
	 */
	public static String validate(byte[] content) {
		String contentType = imageType(content);
		if (WEBP.equals(contentType)) {
			validateWebpChunks(content);
		}
		Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType(contentType);
		if (!readers.hasNext()) {
			throw new IllegalStateException("No image reader for " + contentType);
		}
		ImageReader reader = readers.next();
		try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
			reader.setInput(input);
			reader.addIIOReadWarningListener((source, warning) -> {
				if (!WEBP.equals(contentType) || !warning.startsWith("Ignoring unexpected chunk: ")) {
					throw new InvalidImageException();
				}
			});
			if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_IMAGE_PIXELS) {
				throw new InvalidImageException();
			}
			reader.read(0);
		} catch (IOException exception) {
			throw new InvalidImageException();
		} finally {
			reader.dispose();
		}
		return contentType;
	}

	/** VP8X reports the canvas size. The decoder can allocate using the bitstream dimensions instead. */
	private static void validateWebpChunks(byte[] content) {
		ByteBuffer data = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);
		if (Integer.toUnsignedLong(data.getInt(4)) != content.length - 8L) {
			throw new InvalidImageException();
		}
		validateWebpChunks(content, data, 12, content.length, true);
	}

	private static void validateWebpChunks(byte[] content, ByteBuffer data, int offset, int end, boolean topLevel) {
		while (offset < end) {
			if (end - offset < 8) {
				throw new InvalidImageException();
			}
			String type = ascii(content, offset, 4);
			long size = Integer.toUnsignedLong(data.getInt(offset + 4));
			int payload = offset + 8;
			long paddedSize = size + (size & 1);
			if (paddedSize > end - payload) {
				throw new InvalidImageException();
			}
			switch (type) {
				case "VP8 " -> {
					if (size < 10) {
						throw new InvalidImageException();
					}
					checkWebpSize(data.getShort(payload + 6) & 0x3fff, data.getShort(payload + 8) & 0x3fff);
				}
				case "VP8L" -> {
					if (size < 5) {
						throw new InvalidImageException();
					}
					int dimensions = data.getInt(payload + 1);
					checkWebpSize((dimensions & 0x3fff) + 1, ((dimensions >>> 14) & 0x3fff) + 1);
				}
				case "ANMF" -> {
					if (!topLevel || size < 16) {
						throw new InvalidImageException();
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
			throw new InvalidImageException();
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
		throw new InvalidImageException();
	}

	private static String ascii(byte[] content, int offset, int length) {
		return new String(content, offset, length, StandardCharsets.US_ASCII);
	}

	public static class InvalidImageException extends RuntimeException {
		public InvalidImageException() {
			super(null, null, false, false);
		}
	}
}
