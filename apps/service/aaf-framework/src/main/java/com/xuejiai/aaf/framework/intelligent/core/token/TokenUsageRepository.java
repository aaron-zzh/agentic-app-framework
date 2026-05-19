/**
 * Token 用量仓库。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.core.token;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Token 用量数据访问。 */
public interface TokenUsageRepository extends JpaRepository<TokenUsageRecord, Long> {

    /** 统计用户在时间范围内的总 Token 消耗 */
    @Query("SELECT COALESCE(SUM(t.totalTokens), 0) FROM TokenUsageRecord t " +
            "WHERE t.userId = :userId AND t.createdAt >= :since")
    long sumTotalTokensByUserSince(Long userId, LocalDateTime since);

    /** 统计用户在时间范围内某模型的 Token 消耗 */
    @Query("SELECT COALESCE(SUM(t.totalTokens), 0) FROM TokenUsageRecord t " +
            "WHERE t.userId = :userId AND t.modelId = :modelId AND t.createdAt >= :since")
    long sumTotalTokensByUserAndModelSince(Long userId, String modelId, LocalDateTime since);
}
