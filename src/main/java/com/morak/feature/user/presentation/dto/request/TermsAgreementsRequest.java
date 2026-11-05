package com.morak.feature.user.presentation.dto.request;

import com.morak.feature.user.domain.vo.TermsAgreements;
import jakarta.validation.constraints.NotNull;

public record TermsAgreementsRequest(
		Boolean age14OrOlder,
		Boolean serviceTermsAgreed,
		Boolean privacyAgreed,
		Boolean photoCollectionAgreed,
		@NotNull Boolean marketingAgreed) {

	public TermsAgreements toAgreements() {
		return new TermsAgreements(age14OrOlder, serviceTermsAgreed, privacyAgreed, photoCollectionAgreed,
				marketingAgreed);
	}
}
