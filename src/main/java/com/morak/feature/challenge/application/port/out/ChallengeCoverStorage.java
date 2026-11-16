package com.morak.feature.challenge.application.port.out;

import java.util.List;
import java.util.Map;

public interface ChallengeCoverStorage {

	void upload(String key, byte[] content, String contentType);

	void delete(String key);

	Map<String, String> signDownloads(List<String> keys);
}
