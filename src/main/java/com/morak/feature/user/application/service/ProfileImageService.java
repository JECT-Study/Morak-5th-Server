package com.morak.feature.user.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.image.UploadedImages;
import com.morak.feature.user.application.dto.result.MyUserInfo;
import com.morak.feature.user.application.port.out.ProfileImageStorage;
import com.morak.feature.user.domain.exception.UserErrorCode;
import com.morak.feature.user.domain.vo.UserId;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 마이페이지 프로필 사진 변경(위키 §4.1). 대표 사진(D-17)처럼 백엔드를 거쳐 올리되 공개 버킷에 두고 URL을 저장한다.
 * TODO(D-33): 저장 방식은 PM 확인 전 제안값이다.
 */
@Service
public class ProfileImageService {

	private static final Logger log = LoggerFactory.getLogger(ProfileImageService.class);

	private final Optional<ProfileImageStorage> storage;
	private final UserService users;

	public ProfileImageService(Optional<ProfileImageStorage> storage, UserService users) {
		this.storage = storage;
		this.users = users;
	}

	public MyUserInfo change(UserId userId, byte[] content) {
		if (content.length > UploadedImages.MAX_IMAGE_BYTES) {
			throw new BusinessException(UserErrorCode.PROFILE_IMAGE_TOO_LARGE);
		}
		String contentType = validate(content);
		ProfileImageStorage bucket = storage.orElseThrow(
				() -> new BusinessException(UserErrorCode.PROFILE_IMAGE_STORAGE_UNAVAILABLE));
		String key = "users/" + userId.value() + "/" + UUID.randomUUID();
		String url = bucket.upload(key, content, contentType);
		String previous;
		try {
			previous = users.changeProfileImage(userId, url);
		} catch (RuntimeException exception) {
			deleteQuietly(bucket, key);
			throw exception;
		}
		deleteUploadedImage(previous);
		return users.getMyInfo(userId);
	}

	/** 기본 캐릭터로 되돌린다. 저장소 설정이 없어도 URL은 지운다. */
	public MyUserInfo remove(UserId userId) {
		String previous = users.changeProfileImage(userId, null);
		deleteUploadedImage(previous);
		return users.getMyInfo(userId);
	}

	void deleteUploadedImage(String url) {
		if (url != null) {
			storage.ifPresent(bucket -> bucket.keyOf(url).ifPresent(key -> deleteQuietly(bucket, key)));
		}
	}

	private static String validate(byte[] content) {
		try {
			return UploadedImages.validate(content);
		} catch (UploadedImages.InvalidImageException exception) {
			throw new BusinessException(UserErrorCode.INVALID_PROFILE_IMAGE);
		} catch (IllegalStateException exception) {
			throw new BusinessException(UserErrorCode.PROFILE_IMAGE_STORAGE_UNAVAILABLE);
		}
	}

	private static void deleteQuietly(ProfileImageStorage bucket, String key) {
		try {
			bucket.delete(key);
		} catch (RuntimeException exception) {
			log.warn("Could not remove unused profile image key={}", key);
		}
	}
}
