package com.xuejiai.aaf.config;

import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;

/** JPA 与 Neo4j 共存时的 Neo4j 事务配置。 */
@Configuration(proxyBeanMethods = false)
public class Neo4jTransactionConfig {

    /**
     * 为 Neo4jTemplate 提供专用事务管理器，但不参与默认事务管理器候选，避免抑制 JPA 自动配置。
     */
    @Bean(defaultCandidate = false)
    public Neo4jTransactionManager neo4jTransactionManager(
            Driver driver, DatabaseSelectionProvider databaseSelectionProvider) {
        return new Neo4jTransactionManager(driver, databaseSelectionProvider);
    }
}
