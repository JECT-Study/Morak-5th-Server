package com.morak.feature.chat.application.port.out;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/** 인증 스토리 답글에 필요한 원본 인증 정보를 읽어오는 포트. */
public interface ChatCertificationReader {

	/** certificationId -> 답글 대상. 지정한 챌린지의 인증만 반환하고, 없거나 다른 챌린지의 인증은 결과에 없다. */
	Map<Long, ReplyTarget> findReplyTargets(Long challengeId, Collection<Long> certificationIds);

	record ReplyTarget(Long certificationId, Long authorParticipationId, LocalDate certDate) {}
}
