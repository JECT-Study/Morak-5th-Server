package com.morak.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "morak.auth.jwt.secret=01234567890123456789012345678901") // 해당 JWT 값은 테스트용 더미 값
class DemoApplicationTests {

	@Test
	void contextLoads() {
	}

}
