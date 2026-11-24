package com.morak.feature.certification.application.service;

import com.morak.common.exception.BusinessException;
import com.morak.common.exception.CommonErrorCode;
import com.morak.common.image.UploadedImages;
import com.morak.feature.certification.application.dto.result.SubmittedCertification;
import com.morak.feature.certification.application.port.out.CertificationImageStorage;
import com.morak.feature.certification.application.port.out.CertificationImageUrls;
import com.morak.feature.certification.application.port.out.CertificationSubmitStore;
import com.morak.feature.certification.application.port.out.CertificationSubmitTargets;
import com.morak.feature.certification.application.port.out.CertificationSubmitTargets.Challenge;
import com.morak.feature.certification.application.port.out.CertificationSubmitTargets.Participant;
import com.morak.feature.certification.domain.exception.CertificationErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 인증 제출(위키 §4.4). 하루 1회, 사진 한 장, 제출 후 수정·삭제·재제출 없음.
 *
 * <p>인증 날짜는 요청을 받은 시각의 KST 날짜다. 지난 날짜로 제출할 방법이 없으므로 "당일 23:59까지"는 따로 검사하지 않는다.
 * 퇴장한 사람(참가가 ACTIVE가 아님)과 조기 종료일(챌린지가 FINALIZING)은 상태 검사로 막힌다. 포인트가 오가지 않아 행을 잠그지 않고,
 * 하루 1회는 (participation_id, cert_date) 유니크가 보장한다.
 *
 * <p>TODO(D-34): 경로·응답 필드와 사진 1장 제한은 제안값이다. 면제일 제외는 O-23(면제일 정의)이 정해지면 넣는다.
 */
@Service
public class SubmitCertificationService {

	private static final Logger log = LoggerFactory.getLogger(SubmitCertificationService.class);
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final LocalTime JUDGE_DUE_TIME = LocalTime.NOON;
	public static final int MAX_IMAGE_BYTES = UploadedImages.MAX_IMAGE_BYTES;

	private final CertificationSubmitTargets targets;
	private final CertificationSubmitStore store;
	private final Optional<CertificationImageStorage> storage;
	private final CertificationImageUrls imageUrls;
	private final Clock clock;

	public SubmitCertificationService(CertificationSubmitTargets targets, CertificationSubmitStore store,
			Optional<CertificationImageStorage> storage, CertificationImageUrls imageUrls, Clock clock) {
		this.targets = targets;
		this.store = store;
		this.storage = storage;
		this.imageUrls = imageUrls;
		this.clock = clock;
	}

	public SubmittedCertification submit(long userId, long challengeId, byte[] image) {
		Instant now = clock.instant();
		LocalDate today = LocalDate.ofInstant(now, KST);
		Challenge challenge = targets.findChallenge(challengeId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		if (!challenge.inProgress() || today.isBefore(challenge.startDate()) || today.isAfter(challenge.endDate())) {
			throw new BusinessException(CertificationErrorCode.CHALLENGE_NOT_IN_PROGRESS);
		}
		Participant participant = targets.findParticipant(challengeId, userId)
				.filter(Participant::active)
				.orElseThrow(() -> new BusinessException(CertificationErrorCode.NOT_ACTIVE_PARTICIPANT));
		if (store.exists(participant.participationId(), today)) {
			throw new BusinessException(CertificationErrorCode.ALREADY_SUBMITTED);
		}
		String contentType = validateImage(image);
		CertificationImageStorage bucket = storage.orElseThrow(
				() -> new BusinessException(CertificationErrorCode.IMAGE_STORAGE_UNAVAILABLE));

		String key = "certifications/" + challengeId + "/" + UUID.randomUUID();
		bucket.upload(key, image, contentType);
		Optional<Long> saved;
		try {
			saved = store.save(challengeId, participant.participationId(), today, now,
					judgeDueAt(today, challenge.pointAmount()), key);
		} catch (RuntimeException exception) {
			deleteQuietly(bucket, key);
			throw exception;
		}
		if (saved.isEmpty()) {
			// 같은 날 다른 요청이 먼저 저장했다.
			deleteQuietly(bucket, key);
			throw new BusinessException(CertificationErrorCode.ALREADY_SUBMITTED);
		}
		String url = imageUrls.resolve(List.of(key)).stream().findFirst().orElse(null);
		return new SubmittedCertification(saved.get(), today, "PENDING", now, url);
	}

	/** 포인트 챌린지는 운영진이 판정하고 자동 승인이 없어 null, 그 밖에는 인증 다음 날 12:00 KST(위키 §4.4). */
	static Instant judgeDueAt(LocalDate certDate, int pointAmount) {
		return pointAmount > 0 ? null : certDate.plusDays(1).atTime(JUDGE_DUE_TIME).atZone(KST).toInstant();
	}

	private static String validateImage(byte[] image) {
		if (image.length > MAX_IMAGE_BYTES) {
			throw new BusinessException(CertificationErrorCode.IMAGE_TOO_LARGE);
		}
		try {
			return UploadedImages.validate(image);
		} catch (UploadedImages.InvalidImageException exception) {
			throw new BusinessException(CertificationErrorCode.INVALID_IMAGE);
		} catch (IllegalStateException exception) {
			throw new BusinessException(CertificationErrorCode.IMAGE_STORAGE_UNAVAILABLE);
		}
	}

	private static void deleteQuietly(CertificationImageStorage bucket, String key) {
		try {
			bucket.delete(key);
		} catch (RuntimeException cleanupFailure) {
			log.warn("Could not remove unused certification image key={}", key);
		}
	}
}
