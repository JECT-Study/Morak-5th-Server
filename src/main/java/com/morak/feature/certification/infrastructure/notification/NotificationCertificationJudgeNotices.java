package com.morak.feature.certification.infrastructure.notification;

import com.morak.feature.certification.application.port.out.CertificationJudgeNotices;
import com.morak.feature.certification.domain.vo.CertificationJudge;
import com.morak.feature.chat.application.service.ChatMessageService;
import com.morak.feature.notification.application.dto.NotificationContent;
import com.morak.feature.notification.application.dto.NotifyCommand;
import com.morak.feature.notification.application.service.NotificationService;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** 판정 알림을 알림 도메인으로, 경고 공개를 팀 채팅으로 보낸다. */
@Component
class NotificationCertificationJudgeNotices implements CertificationJudgeNotices {

	private static final String TARGET_CHALLENGE = "CHALLENGE";

	private final NotificationService notifications;
	private final ChatMessageService chat;

	NotificationCertificationJudgeNotices(NotificationService notifications, ChatMessageService chat) {
		this.notifications = notifications;
		this.chat = chat;
	}

	@Override
	public void rejected(long recipientUserId, long challengeId, long certificationId, String challengeTitle,
			LocalDate certDate, int remainingFailCount) {
		// 인증 한 건은 한 번만 거절되지만, 재시도에도 알림이 하나만 남도록 인증 ID로 막는다.
		notifications.notify(new NotifyCommand(recipientUserId,
				new NotificationContent.CertificationRejected(challengeTitle, certDate, remainingFailCount),
				TARGET_CHALLENGE, challengeId, "CERTIFICATION_REJECTED:" + certificationId));
	}

	@Override
	public boolean judgeOverdue(long recipientUserId, long challengeId, String challengeTitle,
			CertificationJudge judge, LocalDate certDate, int warningCount) {
		// 경고는 담당자별 하루 1회(위키 §4.4). 담당 역할·인증 날짜로 막아 배치를 다시 돌려도 늘지 않는다. TODO(D-42)
		return notifications.notify(new NotifyCommand(recipientUserId,
				new NotificationContent.JudgeOverdueWarning(challengeTitle, warningCount),
				TARGET_CHALLENGE, challengeId, "JUDGE_OVERDUE:" + challengeId + ":" + judge + ":" + certDate));
	}

	@Override
	public void judgeOverdueToTeam(long challengeId, CertificationJudge judge, int warningCount) {
		// TODO(FE-O12): 채팅 문구는 미정이라 임시 문구다. 확정되면 교체한다.
		String who = judge == CertificationJudge.HOST ? "방장" : "부방장";
		chat.postSystem(challengeId, who + "이 인증 판정 기한을 지키지 않아 경고가 " + warningCount + "회 누적됐어요.");
	}
}
