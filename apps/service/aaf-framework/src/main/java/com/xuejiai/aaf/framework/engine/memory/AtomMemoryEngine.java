/**
 * 原子记忆引擎接口——引擎层通用执行能力。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.engine.memory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** AtomMemoryEngine：纯算法层，不调用 LLM。 负责原子记忆片段的存储/索引/检索。 */
public interface AtomMemoryEngine {

    // ===== 存储 =====

    /** 存储单条原子 */
    MemoryAtom store(MemoryAtom atom);

    /** 批量存储 */
    List<MemoryAtom> storeBatch(List<MemoryAtom> atoms);

    /** 添加原子间关系 */
    MemoryRelation addRelation(MemoryRelation relation);

    // ===== 检索 =====

    /** 向量相似度检索 */
    List<MemoryAtom> searchByVector(Long userId, float[] queryVec, int topK);

    /** 时间范围检索 */
    List<MemoryAtom> searchByTime(Long userId, Instant start, Instant end);

    /** 混合检索（时序 + 语义 + 标签） */
    List<MemoryAtom> searchHybrid(HybridQuery query);

    /** Bundle Search（图路由证据链检索） */
    List<MemoryBundle> searchBundles(Long userId, float[] queryVec, int topK, Instant queryTime);

    // ===== 生命周期 =====

    /** 按 scope 检索有效记忆（用于程序化记忆等场景） */
    List<MemoryAtom> searchByScope(Long userId, String scope, int topK);

    /** 标记失效 */
    void invalidate(List<UUID> atomIds);

    /** 更新权重 */
    void updateWeight(UUID atomId, double weight);

    /**
     * 记录程序化记忆的使用结果，按成功率动态更新 weight。
     *
     * @param atomId 原子记忆 ID（scope='procedural'）
     * @param success 本次使用是否成功
     */
    void recordUsage(UUID atomId, boolean success);

    /** 删除 */
    void delete(List<UUID> atomIds);
}
