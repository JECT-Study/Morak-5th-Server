package com.morak.feature.challenge.application.port.out;

public interface ChallengeCoverStorage {

	void upload(String key, byte[] content, String contentType);

	void delete(String key);
}
