package com.xuejiai.aaf;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 应用启动类存在性验证（不启动完整上下文，避免依赖外部数据库）。 */
class AafApplicationTest {

    @Test
    void applicationClassExists() {
        assertThat(AafApplication.class).isNotNull();
    }
}
