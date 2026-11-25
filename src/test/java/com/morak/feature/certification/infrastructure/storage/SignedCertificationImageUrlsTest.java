package com.morak.feature.certification.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.morak.common.exception.BusinessException;
import com.morak.feature.certification.application.port.out.CertificationImageStorage;
import com.morak.feature.certification.domain.exception.CertificationErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SignedCertificationImageUrlsTest {

	private final CertificationImageStorage storage = mock(CertificationImageStorage.class);

	@Test
	void signsKeysInOrderAndDropsKeysThatWereNotSigned() {
		when(storage.signDownloads(List.of("a", "b", "c"))).thenReturn(Map.of("c", "signed-c", "a", "signed-a"));

		assertEquals(List.of("signed-a", "signed-c"),
				new SignedCertificationImageUrls(Optional.of(storage)).resolve(List.of("a", "b", "c")));
	}

	@Test
	void returnsNoUrlsWithoutStorageOrWhenSigningFails() {
		when(storage.signDownloads(List.of("a")))
				.thenThrow(new BusinessException(CertificationErrorCode.IMAGE_STORAGE_UNAVAILABLE));

		assertEquals(List.of(), new SignedCertificationImageUrls(Optional.empty()).resolve(List.of("a")));
		assertEquals(List.of(), new SignedCertificationImageUrls(Optional.of(storage)).resolve(List.of("a")));
		assertEquals(List.of(), new SignedCertificationImageUrls(Optional.of(storage)).resolve(List.of()));
	}
}
