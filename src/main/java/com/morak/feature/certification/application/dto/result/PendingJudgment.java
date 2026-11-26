package com.morak.feature.certification.application.dto.result;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 내가 판정할 인증 한 건(홈 카드 "판정하기").
 *
 * @param role 제출자의 HOST / DEPUTY / MEMBER
 * @param judgeDueAt 판정 마감(인증 다음 날 12:00 KST). 지나면 자동 승인된다
 * @param imageUrl 열람용 서명 주소(300초). 서명에 실패하면 null
 */
public record PendingJudgment(long certificationId, long userId, String nickname, String profileImageUrl, String role,
		LocalDate certDate, Instant submittedAt, Instant judgeDueAt, String imageUrl) {}
