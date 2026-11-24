package com.morak.feature.user.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.common.image.UploadedImages;
import com.morak.feature.user.application.port.out.ProfileImageStorage;
import com.morak.feature.user.domain.exception.UserErrorCode;
import com.morak.feature.user.domain.vo.UserId;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProfileImageServiceTest {

	private static final UserId USER = new UserId(7L);
	private static final byte[] PNG = Base64.getDecoder().decode(
			"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jD40AAAAASUVORK5CYII=");

	private final ProfileImageStorage storage = mock(ProfileImageStorage.class);
	private final UserService users = mock(UserService.class);
	private final ProfileImageService service = new ProfileImageService(Optional.of(storage), users);

	@Test
	void uploadsPhotoSavesItsUrlAndRemovesThePreviousUpload() {
		when(storage.upload(startsWith("users/7/"), eq(PNG), eq("image/png"))).thenReturn("https://cdn/new");
		when(users.changeProfileImage(USER, "https://cdn/new")).thenReturn("https://cdn/old");
		when(storage.keyOf("https://cdn/old")).thenReturn(Optional.of("users/7/old"));

		service.change(USER, PNG);

		verify(storage).delete("users/7/old");
		verify(users).getMyInfo(USER);
	}

	@Test
	void keepsSocialPhotoUrlItDidNotUpload() {
		when(storage.upload(anyString(), any(), anyString())).thenReturn("https://cdn/new");
		when(users.changeProfileImage(USER, "https://cdn/new")).thenReturn("https://k.kakaocdn.net/me.png");
		when(storage.keyOf("https://k.kakaocdn.net/me.png")).thenReturn(Optional.empty());

		service.change(USER, PNG);

		verify(storage, never()).delete(anyString());
	}

	@Test
	void removesNewUploadWhenSavingFails() {
		when(storage.upload(anyString(), any(), anyString())).thenReturn("https://cdn/new");
		when(users.changeProfileImage(USER, "https://cdn/new")).thenThrow(new IllegalStateException("db"));

		assertThrows(IllegalStateException.class, () -> service.change(USER, PNG));

		var key = ArgumentCaptor.forClass(String.class);
		verify(storage).upload(key.capture(), any(), anyString());
		verify(storage).delete(key.getValue());
	}

	@Test
	void oldPhotoCleanupFailureDoesNotFailTheChange() {
		when(storage.upload(anyString(), any(), anyString())).thenReturn("https://cdn/new");
		when(users.changeProfileImage(USER, "https://cdn/new")).thenReturn("https://cdn/old");
		when(storage.keyOf("https://cdn/old")).thenReturn(Optional.of("users/7/old"));
		doThrow(new BusinessException(UserErrorCode.PROFILE_IMAGE_STORAGE_UNAVAILABLE)).when(storage).delete("users/7/old");

		service.change(USER, PNG);

		verify(users).getMyInfo(USER);
	}

	@Test
	void rejectsOversizedAndInvalidPhotosBeforeUploading() {
		assertError(UserErrorCode.PROFILE_IMAGE_TOO_LARGE, new byte[UploadedImages.MAX_IMAGE_BYTES + 1]);
		assertError(UserErrorCode.INVALID_PROFILE_IMAGE, "not an image".getBytes());
		verify(storage, never()).upload(anyString(), any(), anyString());
	}

	@Test
	void reportsUnavailableWhenStorageIsNotConfigured() {
		var withoutStorage = new ProfileImageService(Optional.empty(), users);
		var error = assertThrows(BusinessException.class, () -> withoutStorage.change(USER, PNG));
		assertEquals(UserErrorCode.PROFILE_IMAGE_STORAGE_UNAVAILABLE, error.getErrorCode());
	}

	private void assertError(UserErrorCode expected, byte[] content) {
		var error = assertThrows(BusinessException.class, () -> service.change(USER, content));
		assertEquals(expected, error.getErrorCode());
	}
}
