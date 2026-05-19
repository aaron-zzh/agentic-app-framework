/**
 * 记忆关系 Repository。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.engine.memory;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MemoryRelationRepository extends JpaRepository<MemoryRelation, UUID> {

    /** 查找原子的所有出边 */
    List<MemoryRelation> findBySourceId(UUID sourceId);

    /** 查找原子的所有入边 */
    List<MemoryRelation> findByTargetId(UUID targetId);

    /** 查找原子的所有关联（出边 + 入边） */
    @Query("""
        SELECT r FROM MemoryRelation r
        WHERE r.sourceId = :atomId OR r.targetId = :atomId
        """)
    List<MemoryRelation> findByAtomId(UUID atomId);

    /** 批量查找多个原子的关联 */
    @Query("""
        SELECT r FROM MemoryRelation r
        WHERE r.sourceId IN :atomIds OR r.targetId IN :atomIds
        """)
    List<MemoryRelation> findByAtomIds(List<UUID> atomIds);
}
