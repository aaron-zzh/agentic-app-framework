/**
 * 记忆原子 Repository。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.engine.memory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MemoryAtomRepository extends JpaRepository<MemoryAtom, UUID> {

    /** 按用户和范围查询指定时点有效的记忆。 */
    @Query(
            """
        SELECT m FROM MemoryAtom m
        WHERE m.userId = :userId AND m.scope = :scope
          AND m.validFrom <= :at AND (m.validTo IS NULL OR m.validTo > :at)
        """)
    List<MemoryAtom> findCurrentByUserIdAndScope(Long userId, String scope, Instant at);

    /** 按用户批量加载指定时点有效的记忆。 */
    @Query(
            """
        SELECT m FROM MemoryAtom m
        WHERE m.userId = :userId AND m.id IN :ids
          AND m.validFrom <= :at AND (m.validTo IS NULL OR m.validTo > :at)
        """)
    List<MemoryAtom> findCurrentByIds(Long userId, List<UUID> ids, Instant at);

    /** 按时间范围查询指定时点有效的记忆。 */
    @Query(
            """
        SELECT m FROM MemoryAtom m
        WHERE m.userId = :userId
          AND m.validFrom <= :at AND (m.validTo IS NULL OR m.validTo > :at)
          AND m.eventTime BETWEEN :start AND :end
        ORDER BY m.eventTime DESC
        """)
    List<MemoryAtom> findByTimeRange(Long userId, Instant start, Instant end, Instant at);

    /** 向量相似度检索（原生 SQL，PgVector cosine distance）。 */
    @Query(
            value =
                    """
        SELECT * FROM ai_memory_atom
        WHERE user_id = :userId
          AND valid_from <= :at AND (valid_to IS NULL OR valid_to > :at)
          AND embedding IS NOT NULL
        ORDER BY embedding <=> cast(:queryVec AS vector)
        LIMIT :topK
        """,
            nativeQuery = true)
    List<MemoryAtom> searchByVector(Long userId, String queryVec, int topK, Instant at);

    /** 更新权重。 */
    @Modifying
    @Query("UPDATE MemoryAtom m SET m.weight = :weight WHERE m.id = :id")
    void updateWeight(UUID id, double weight);

    /** 更新访问信息。 */
    @Modifying
    @Query(
            """
        UPDATE MemoryAtom m
        SET m.accessCount = m.accessCount + 1, m.lastAccessedAt = :now
        WHERE m.id IN :ids
        """)
    void recordAccess(List<UUID> ids, Instant now);

    /** 标记失效。 */
    @Modifying
    @Query("UPDATE MemoryAtom m SET m.validTo = :now WHERE m.id IN :ids")
    void invalidate(List<UUID> ids, Instant now);

    /** 查找长期未访问且在指定时点有效的原子。 */
    @Query(
            """
        SELECT m FROM MemoryAtom m
        WHERE m.validFrom <= :at AND (m.validTo IS NULL OR m.validTo > :at)
          AND (m.lastAccessedAt IS NULL OR m.lastAccessedAt < :cutoff)
        """)
    List<MemoryAtom> findStaleAtoms(Instant cutoff, Instant at);
}
