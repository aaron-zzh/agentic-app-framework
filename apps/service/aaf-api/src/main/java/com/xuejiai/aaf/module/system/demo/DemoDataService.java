package com.xuejiai.aaf.module.system.demo;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 演示数据服务：执行 db/testdata/R__test_data.sql 加载或清理演示数据。 仅在 aaf.demo.enabled=true 时生效。
 *
 * @author AaronZZH
 */
@Slf4j
@Service
@ConditionalOnProperty("aaf.demo.enabled")
@RequiredArgsConstructor
public class DemoDataService {

    private static final String DEMO_SQL = "classpath:db/testdata/R__test_data.sql";

    private final DataSource dataSource;
    private final ResourceLoader resourceLoader;

    /** 执行演示数据 SQL，幂等（SQL 内部用 NOT EXISTS / ON CONFLICT 防重复）。 */
    public void load() {
        log.info("[Demo] 加载演示数据...");
        var resource = resourceLoader.getResource(DEMO_SQL);
        try (Connection conn = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conn, resource);
            log.info("[Demo] 演示数据加载完成");
        } catch (SQLException e) {
            throw new RuntimeException("演示数据加载失败", e);
        }
    }

    /** 清理演示数据（删除 user1/user2 及其关联数据、demo 通知）。 */
    public void clean() {
        log.info("[Demo] 清理演示数据...");
        try (Connection conn = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(
                    conn, resourceLoader.getResource("classpath:db/testdata/clean_demo_data.sql"));
            log.info("[Demo] 演示数据清理完成");
        } catch (SQLException e) {
            throw new RuntimeException("演示数据清理失败", e);
        }
    }
}
