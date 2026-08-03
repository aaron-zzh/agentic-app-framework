package com.xuejiai.aaf.config;

import org.neo4j.driver.Driver;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 启动时验证 Neo4j 连通性，并在连接成功后打印醒目标识。 */
@Slf4j
@Component
@RequiredArgsConstructor
public final class Neo4jConnectionLogger implements ApplicationRunner {

    static final String SUCCESS_MESSAGE =
            """

            ============================================================
                         Neo4j 数据库连接成功
            ============================================================
            """;

    private final Driver driver;

    @Override
    public void run(ApplicationArguments args) {
        try {
            driver.verifyConnectivity();
            log.info(SUCCESS_MESSAGE);
        } catch (RuntimeException exception) {
            log.warn("Neo4j 数据库连接失败，图数据库相关功能暂不可用，应用将继续启动", exception);
        }
    }
}
