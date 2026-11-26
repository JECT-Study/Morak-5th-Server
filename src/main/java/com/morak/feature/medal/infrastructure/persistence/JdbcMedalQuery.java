package com.morak.feature.medal.infrastructure.persistence;

import com.morak.feature.medal.application.query.MedalQuery;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 메달 엔티티가 생기기 전까지의 조회 구현. 엔티티/리포지토리가 생기면 그쪽으로 교체한다. */
@Component
public class JdbcMedalQuery implements MedalQuery {

	private final JdbcClient jdbc;

	public JdbcMedalQuery(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Map<Long, List<MedalInfo>> findEarnedByUserIds(Collection<Long> userIds) {
		Map<Long, List<MedalInfo>> result = new LinkedHashMap<>();
		if (userIds.isEmpty()) {
			return result;
		}
		Set<String> seen = new HashSet<>();
		jdbc.sql("""
				SELECT um.user_id, m.code, m.name, m.icon_url
				FROM morak.user_medal um
				JOIN morak.medal m ON m.id = um.medal_id
				WHERE um.user_id IN (:userIds)
				ORDER BY um.user_id, um.earned_at, m.id
				""")
				.param("userIds", userIds)
				.query((rs, rowNum) -> {
					long userId = rs.getLong("user_id");
					String code = rs.getString("code");
					if (seen.add(userId + ":" + code)) {
						result.computeIfAbsent(userId, id -> new ArrayList<>())
								.add(new MedalInfo(code, rs.getString("name"), rs.getString("icon_url")));
					}
					return code;
				})
				.list();
		return result;
	}
}
