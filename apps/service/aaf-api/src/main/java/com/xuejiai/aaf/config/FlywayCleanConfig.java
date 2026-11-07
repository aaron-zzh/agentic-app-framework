package com.xuejiai.aaf.config;

import java.util.ArrayList;

import org.flywaydb.core.Flyway;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * 开发环境 Flyway 自动清理配置。
 *
 * <p>开启 aaf.flyway.clean-on-start=true 时，应用启动会先执行 clean 再 migrate， 适用于开发阶段频繁修改迁移脚本的场景。生产环境禁止开启。
 *
 * <p>PostgreSQL 重建后主键从头分配，同步清理依附于旧库数据的外部存储，避免与新数据冲突：
 *
 * <ul>
 *   <li>Neo4j：删除全部 {@code KnowledgeEntity} 投影节点（PostgreSQL 为真理源，投影随事实重新生成）
 *   <li>Redis：删除任务队列与去重标记（{@code task_queue:*}）及知识文档恢复游标（{@code knowledge-document:*}）
 * </ul>
 *
 * <p>外部存储不可用时仅记录告警，不阻断启动。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "aaf.flyway.clean-on-start", havingValue = "true")
public class FlywayCleanConfig {

    private static final String[] REDIS_KEY_PATTERNS = {"task_queue:*", "knowledge-document:*"};

    @Bean
    public FlywayMigrationInitializer flywayInitializer(
            Flyway flyway,
            ObjectProvider<Driver> neo4jDriver,
            ObjectProvider<StringRedisTemplate> redisTemplate) {
        return new FlywayMigrationInitializer(
                flyway,
                f -> {
                    f.clean();
                    f.migrate();
                    neo4jDriver.ifAvailable(FlywayCleanConfig::cleanKnowledgeGraph);
                    redisTemplate.ifAvailable(FlywayCleanConfig::cleanRedisKeys);
                });
    }

    private static void cleanKnowledgeGraph(Driver driver) {
        try (var session = driver.session()) {
            var deleted =
                    session.run(
                                    "MATCH (e:KnowledgeEntity) DETACH DELETE e RETURN count(*) AS deleted")
                            .single()
                            .get("deleted")
                            .asLong();
            log.info("[开发清库] 已清理 Neo4j 知识图投影节点: {}", deleted);
        } catch (RuntimeException failure) {
            log.warn("[开发清库] 清理 Neo4j 知识图投影失败，已跳过: {}", failure.getMessage());
        }
    }

    private static void cleanRedisKeys(StringRedisTemplate redisTemplate) {
        try {
            var keys = new ArrayList<String>();
            for (var pattern : REDIS_KEY_PATTERNS) {
                try (var cursor =
                        redisTemplate.scan(
                                ScanOptions.scanOptions().match(pattern).count(1000).build())) {
                    cursor.forEachRemaining(keys::add);
                }
            }
            var deleted = keys.isEmpty() ? 0L : redisTemplate.delete(keys);
            log.info("[开发清库] 已清理 Redis 任务与恢复键: {}", deleted);
        } catch (RuntimeException failure) {
            log.warn("[开发清库] 清理 Redis 任务键失败，已跳过: {}", failure.getMessage());
        }
    }
}
