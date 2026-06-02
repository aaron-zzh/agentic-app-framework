package com.xuejiai.aaf;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 全上下文启动冒烟测试：需真实 Postgres/Redis/Neo4j，属集成测试（Failsafe）。 由 tester 经 {@code pnpm nx acceptance
 * service} 运行，不进 developer 单测内循环。
 */
@SpringBootTest
@ActiveProfiles("test")
class AafApplicationIT {

    @Test
    void contextLoads() {}
}
