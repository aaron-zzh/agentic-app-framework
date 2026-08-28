package com.xuejiai.aaf.framework.intelligent.cognition.port;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.engine.memory.MemoryAtom;
import com.xuejiai.aaf.framework.engine.memory.MemoryBundle;

/**
 * 记忆自有检索入口：原子记忆、情景 bundle、程序化记忆三通道并行检索。
 *
 * <p>只负责单一来源（记忆）内部的通道检索，不做跨源（记忆 + 知识库）预算分配、不做跨源融合与重排——这些职责属于上一层的
 * {@code UnifiedRetrievalPort}（见 retrieval.md）。短期会话上下文与任务工作记忆不经本入口，由 L1
 * 前置直接注入。
 */
public interface MemoryRetrievalPort {

    /**
     * 按预算并行检索原子、情景 bundle、程序化三通道。
     *
     * @param query 检索请求，含各通道配额
     * @return 三通道候选结果
     */
    MemoryRetrievalResult retrieve(MemoryRetrievalQuery query);

    /**
     * 记忆检索请求。
     *
     * @param userId 用户 ID
     * @param queryText 查询文本
     * @param queryEmbedding 查询向量；为空时跳过依赖向量的通道
     * @param atomicTopK 原子记忆配额；<=0 表示不检索该通道
     * @param episodicTopK 情景 bundle 配额；<=0 表示不检索该通道
     * @param proceduralTopK 程序化记忆配额；<=0 表示不检索该通道
     * @param at 请求时间，用于情景 bundle 的时态判断
     */
    record MemoryRetrievalQuery(
            Long userId,
            String queryText,
            float[] queryEmbedding,
            int atomicTopK,
            int episodicTopK,
            int proceduralTopK,
            Instant at) {
        public MemoryRetrievalQuery {
            Objects.requireNonNull(userId, "userId 不能为空");
            queryText = Objects.requireNonNullElse(queryText, "").trim();
            Objects.requireNonNull(at, "at 不能为空");
        }
    }

    /**
     * 记忆检索结果：三通道候选，各自保留原始排序，不跨通道合并。
     *
     * @param atomicMemories 原子记忆候选（语义近邻，已按向量召回序）
     * @param episodicBundles 情景证据链候选
     * @param proceduralMemories 程序化记忆候选（scope='procedural'）
     */
    record MemoryRetrievalResult(
            List<MemoryAtom> atomicMemories,
            List<MemoryBundle> episodicBundles,
            List<MemoryAtom> proceduralMemories) {
        public MemoryRetrievalResult {
            atomicMemories = atomicMemories == null ? List.of() : List.copyOf(atomicMemories);
            episodicBundles = episodicBundles == null ? List.of() : List.copyOf(episodicBundles);
            proceduralMemories =
                    proceduralMemories == null ? List.of() : List.copyOf(proceduralMemories);
        }

        public static MemoryRetrievalResult empty() {
            return new MemoryRetrievalResult(List.of(), List.of(), List.of());
        }
    }
}
