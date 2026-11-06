package com.morak.feature.chat.infrastructure.certification;

import com.morak.feature.certification.application.query.CertificationQuery;
import com.morak.feature.chat.application.port.out.ChatCertificationReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 채팅 포트를 인증 모듈의 공개 조회 API로 연결한다. */
@Component
public class CertificationChatReaderAdapter implements ChatCertificationReader {

	private final CertificationQuery certificationQuery;

	public CertificationChatReaderAdapter(CertificationQuery certificationQuery) {
		this.certificationQuery = certificationQuery;
	}

	@Override
	public Map<Long, ReplyTarget> findReplyTargets(Long challengeId, Collection<Long> certificationIds) {
		Map<Long, ReplyTarget> result = new HashMap<>();
		certificationQuery.findInChallenge(challengeId, certificationIds).forEach((id, info) ->
				result.put(id, new ReplyTarget(info.certificationId(), info.participationId(), info.certDate())));
		return result;
	}
}
