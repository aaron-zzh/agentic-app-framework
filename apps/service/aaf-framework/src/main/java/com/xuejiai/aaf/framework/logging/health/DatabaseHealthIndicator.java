package com.xuejiai.aaf.framework.logging.health;

import javax.sql.DataSource;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** 数据库连通性增强健康检查（补充连接池信息）。 */
@Component("aafDatabaseHealth")
public class DatabaseHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (var conn = dataSource.getConnection();
                var stmt = conn.createStatement();
                var rs = stmt.executeQuery("SELECT 1")) {
            if (rs.next()) {
                return Health.up()
                        .withDetail("database", conn.getMetaData().getDatabaseProductName())
                        .withDetail("url", conn.getMetaData().getURL())
                        .build();
            }
            return Health.down().withDetail("reason", "SELECT 1 返回空").build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
