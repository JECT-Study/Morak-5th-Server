package com.morak.feature.user.infrastructure.participation;

import com.morak.feature.medal.application.query.MedalQuery;
import com.morak.feature.participation.application.dto.result.MyRecordView;
import com.morak.feature.participation.application.service.ReadMyRecordService;
import com.morak.feature.user.application.port.out.UserRecordReader;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 인증률·모락이 단계·완주 계산은 내 기록과 같은 ReadMyRecordService를 쓰고, 메달만 덧붙인다. */
@Component
class UserRecordReaderAdapter implements UserRecordReader {

	private final ReadMyRecordService records;
	private final MedalQuery medalQuery;

	UserRecordReaderAdapter(ReadMyRecordService records, MedalQuery medalQuery) {
		this.records = records;
		this.medalQuery = medalQuery;
	}

	@Override
	public UserRecord read(long userId) {
		MyRecordView record = records.read(userId);
		List<Medal> medals = medalQuery.findEarnedByUserIds(Set.of(userId)).getOrDefault(userId, List.of()).stream()
				.map(medal -> new Medal(medal.code(), medal.name(), medal.iconUrl()))
				.toList();
		return new UserRecord(record.certificationRate(), record.morakiStage(), record.hasCertificationRecord(),
				record.completedCount(), record.midwayFailCount(), medals);
	}
}
