/**
 * 程序化记忆仓库。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.memory;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** 程序化记忆数据访问。 */
public interface ProceduralMemoryRepository extends JpaRepository<ProceduralMemory, Long> {

    /** 按任务类型查找（用户私有 + 全局共享） */
    @Query("SELECT m FROM ProceduralMemory m WHERE m.taskType = :taskType " +
            "AND (m.userId = :userId OR m.userId IS NULL) ORDER BY m.qualityScore DESC")
    List<ProceduralMemory> findByTaskType(String taskType, Long userId);

    /** 按分类查找 */
    List<ProceduralMemory> findByCategoryOrderByQualityScoreDesc(String category);
}
