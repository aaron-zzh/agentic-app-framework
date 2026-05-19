/**
 * 长期记忆仓库。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.memory;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** 长期记忆数据访问。 */
public interface LongTermMemoryRepository extends JpaRepository<LongTermMemory, Long> {

    /** 按重要性降序获取用户记忆 */
    List<LongTermMemory> findByUserIdOrderByImportanceDesc(Long userId);

    /** 获取用户最近的记忆 */
    List<LongTermMemory> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);

    /** 获取低价值记忆（用于归档/清理） */
    @Query("SELECT m FROM LongTermMemory m WHERE m.userId = :userId " +
            "AND m.importance < :threshold AND m.lastAccessedAt < :before")
    List<LongTermMemory> findLowValueMemories(Long userId, Double threshold, LocalDateTime before);

    /** 按类型查找 */
    List<LongTermMemory> findByUserIdAndMemoryType(Long userId, String memoryType);
}
