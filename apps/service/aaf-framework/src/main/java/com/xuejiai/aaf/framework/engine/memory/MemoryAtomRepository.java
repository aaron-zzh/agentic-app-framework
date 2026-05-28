/**
 * 记忆原子 Repository。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.engine.memory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MemoryAtomRepository extends JpaRepository<MemoryAtom, UUID> {

    /** 按用户分页查询 */
    Page<MemoryAtom> findByUserId(Long userId, Pageable pageable);

    /** 按用户和范围分页查询 */
    Page<MemoryAtom> findByUserIdAndScope(Long userId, String scope, Pageable pageable);

    /** 按用户和范围查询（不分页） */
    List<MemoryAtom> findByUserIdAndScope(Long userId, String scope);

    /** 按用户和范围统计数量 */
    long countByUserIdAndScope(Long userId, String scope);

    /** 关键词搜索 */
    List<MemoryAtom> findByUserIdAndContentContaining(Long userId, String keyword);

    /** 按范围关键词搜索 */
    List<MemoryAtom> findByUserIdAndScopeAndContentContaining(Long userId, String scope, String keyword);

    /** 按用户和范围查询有效记忆 */
    List<MemoryAtom> findByUserIdAndScopeAndValidToIsNull(Long userId, String scope);

    /** 按时间范围查询 */
    @Query(
            """
        SELECT m FROM MemoryAtom m
        WHERE m.userId = :userId AND m.validTo IS NULL
          AND m.eventTime BETWEEN :start AND :end
        ORDER BY m.eventTime DESC
        """)
    List<MemoryAtom> findByTimeRange(Long userId, Instant start, Instant end);

    /** 向量相似度检索（原生 SQL，PgVector cosine distance） */
    @Query(
            value =
                    """
        SELECT * FROM memory_atom
        WHERE user_id = :userId AND valid_to IS NULL
        ORDER BY embedding <=> cast(:queryVec AS vector)
        LIMIT :topK
        """,
            nativeQuery = true)
    List<MemoryAtom> searchByVector(Long userId, String queryVec, int topK);

    /** 更新权重 */
    @Modifying
    @Query("UPDATE MemoryAtom m SET m.weight = :weight WHERE m.id = :id")
    void updateWeight(UUID id, double weight);

    /** 更新访问信息 */
    @Modifying
    @Query(
            """
        UPDATE MemoryAtom m
        SET m.accessCount = m.accessCount + 1, m.lastAccessedAt = :now
        WHERE m.id IN :ids
        """)
    void recordAccess(List<UUID> ids, Instant now);

    /** 标记失效 */
    @Modifying
    @Query("UPDATE MemoryAtom m SET m.validTo = :now WHERE m.id IN :ids")
    void invalidate(List<UUID> ids, Instant now);
}
