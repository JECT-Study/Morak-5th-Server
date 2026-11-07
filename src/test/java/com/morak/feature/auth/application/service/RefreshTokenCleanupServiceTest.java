package com.morak.feature.auth.application.service;

import com.morak.feature.auth.domain.repository.RefreshTokenRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefreshTokenCleanupServiceTest {
	@Test
	void appliesConfiguredRetentionToDeletionCutoff() {
		RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
		RefreshTokenCleanupService service = new RefreshTokenCleanupService(repository, Duration.ofDays(3));
		LocalDateTime before = LocalDateTime.now().minusDays(3);
		service.cleanup();
		LocalDateTime after = LocalDateTime.now().minusDays(3);
		ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(repository).deleteExpiredBefore(cutoff.capture());
		assertFalse(cutoff.getValue().isBefore(before));
		assertFalse(cutoff.getValue().isAfter(after));
	}

	@Test
	void rejectsNegativeRetentionButAllowsImmediateExpiredTokenDeletion() {
		RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
		assertThrows(IllegalStateException.class,
				() -> new RefreshTokenCleanupService(repository, Duration.ofDays(-1)));
		assertDoesNotThrow(() -> new RefreshTokenCleanupService(repository, Duration.ZERO));
	}
}
