package com.xuejiai.aaf.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 开发环境 Flyway 自动清理配置。
 *
 * <p>开启 aaf.flyway.clean-on-start=true 时，应用启动会先执行 clean 再 migrate， 适用于开发阶段频繁修改迁移脚本的场景。生产环境禁止开启。
 */
@Configuration
@ConditionalOnProperty(name = "aaf.flyway.clean-on-start", havingValue = "true")
public class FlywayCleanConfig {

    @Bean
    public FlywayMigrationInitializer flywayInitializer(Flyway flyway) {
        return new FlywayMigrationInitializer(
                flyway,
                f -> {
                    f.clean();
                    f.migrate();
                });
    }
}
