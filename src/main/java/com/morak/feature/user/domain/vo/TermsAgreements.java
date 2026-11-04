package com.morak.feature.user.domain.vo;

public record TermsAgreements(
		Boolean age14OrOlder,
		Boolean serviceTermsAgreed,
		Boolean privacyAgreed,
		Boolean photoCollectionAgreed,
		boolean marketingAgreed) {

	public boolean acceptsAllRequired() {
		return Boolean.TRUE.equals(age14OrOlder) && Boolean.TRUE.equals(serviceTermsAgreed)
				&& Boolean.TRUE.equals(privacyAgreed) && Boolean.TRUE.equals(photoCollectionAgreed);
	}
}
