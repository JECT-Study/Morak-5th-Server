package com.morak.feature.challenge.application.command;

import java.util.Optional;

/**
 * 다른 모듈(참가 등)에 공개하는 챌린지 변경 API.
 *
 * <p>{@link #lockForUpdate}, {@link #acceptMember}, {@link #changeOwner}는 호출한 쪽의 트랜잭션에 참여한다. 트랜잭션 없이 호출하면 예외가 난다.
 */
public interface ChallengeCommand {

	/** 잠금 없이 챌린지를 읽는다. 개설 후 바뀌지 않는 값(포인트 등)을 락 순서 때문에 먼저 알아야 할 때 쓴다. */
	Optional<ChallengeForApplication> find(long challengeId);

	/** 챌린지 행을 잠그고 읽는다. 챌린지가 없으면 empty. */
	Optional<ChallengeForApplication> lockForUpdate(long challengeId);

	/** 승인으로 참여 인원을 1 늘린다. 정원이 찼으면 예외. */
	void acceptMember(long challengeId);

	/** 방장·부방장 승계로 챌린지의 방장(owner)을 바꾼다. 챌린지가 없으면 예외. */
	void changeOwner(long challengeId, long newOwnerUserId);

	/**
	 * 지원·승인 판단에 필요한 챌린지 정보. visibility, status는 위키 §5 enum 문자열 그대로다.
	 *
	 * @param inviteCode 비공개 챌린지의 초대 코드 검증에 쓴다
	 */
	record ChallengeForApplication(
			long challengeId,
			long ownerUserId,
			String title,
			String visibility,
			String inviteCode,
			boolean recruiting,
			int capacity,
			int currentCount,
			int pointAmount) {

		public boolean isFull() {
			return currentCount >= capacity;
		}
	}
}
