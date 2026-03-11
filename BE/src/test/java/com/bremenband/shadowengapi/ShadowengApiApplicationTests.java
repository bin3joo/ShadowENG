package com.bremenband.shadowengapi;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("실제 PostgreSQL/Redis 인프라가 필요한 통합 테스트 — CI/CD 환경에서 실행")
@SpringBootTest
class ShadowengApiApplicationTests {

    @Test
    void contextLoads() {
    }

}
