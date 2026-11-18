package com.morak.feature.medal.application.query;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 다른 모듈(참가 등)에 공개하는 메달 조회 API. */
public interface MedalQuery {

	/** userId -> 획득한 메달(획득 순). 같은 메달은 한 번만 담고, 메달이 없는 사용자는 결과에 없다. */
	Map<Long, List<MedalInfo>> findEarnedByUserIds(Collection<Long> userIds);

	record MedalInfo(String code, String name, String iconUrl) {}
}
