/**
 * 记忆融合检索服务（对齐认知心理学：线索依赖提取 + 激活扩散）。
 *
 * <p>认知心理学对齐：人类记忆检索不是全量扫描，而是：
 *
 * <ol>
 *   <li>线索识别：根据查询线索判断应激活哪类记忆
 *   <li>激活扩散：从种子记忆沿关系网络扩散
 *   <li>提取重构：将激活的片段重新组装为连贯回忆
 * </ol>
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingService;
import com.xuejiai.aaf.framework.engine.memory.AtomMemoryEngine;
import com.xuejiai.aaf.framework.engine.memory.HybridQuery;
import com.xuejiai.aaf.framework.engine.memory.MemoryAtom;
import com.xuejiai.aaf.framework.engine.memory.MemoryBundle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 多源融合检索：根据查询意图动态路由到不同记忆类型， 分配检索预算，并行检索后重排输出。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryRetrievalService {

    private final ShortTermMemoryService shortTermMemory;
    private final GraphMemoryService graphMemory;
    private final AtomMemoryEngine atomMemoryEngine;
    private final EmbeddingService embeddingService;
    private final MemoryRerankerService reranker;

    // ===== 默认预算分配（借鉴 M-FLOW OrchestratorConfig） =====
    private static final int ATOMIC_TOP_K = 6;
    private static final int EPISODIC_TOP_K = 4;
    private static final int PROCEDURAL_TOP_K = 2;

    /**
     * 完整检索流程（对齐认知心理学提取过程）。
     *
     * <p>流程：线索识别 → 预算分配 → 并行检索 → 重排 → 格式化输出
     *
     * @param userId 用户 ID
     * @param conversationId 对话 ID（用于短期记忆）
     * @param query 查询文本
     * @return 分区记忆上下文
     */
    public MemoryContext retrieve(Long userId, String conversationId, String query) {
        var context = new MemoryContext();

        // 1. 短期记忆：当前对话上下文（工作记忆的一部分）
        if (conversationId != null) {
            context.setRecentMessages(shortTermMemory.getContext(conversationId, 10));
        }

        // 2. 查询意图路由 + 预算分配
        var intent = classifyIntent(query);
        var budget = allocateBudget(intent);

        // 3. 生成查询向量
        float[] queryEmbedding = null;
        if (query != null && !query.isBlank()) {
            queryEmbedding = embeddingService.embed(query);
        }

        // 4. 按预算并行检索各类记忆
        if (queryEmbedding != null && budget.atomicTopK > 0) {
            var atomicResults =
                    atomMemoryEngine.searchByVector(userId, queryEmbedding, budget.atomicTopK);
            context.setAtomicMemories(atomicResults);
        }

        if (queryEmbedding != null && budget.episodicTopK > 0) {
            var bundles =
                    atomMemoryEngine.searchBundles(
                            userId, queryEmbedding, budget.episodicTopK, null);
            context.setEpisodicBundles(bundles);
        }

        if (budget.proceduralTopK > 0 && intent == QueryIntent.PROCEDURAL) {
            context.setProceduralMemories(
                    atomMemoryEngine.searchByScope(userId, "procedural", budget.proceduralTopK));
        }

        // 5. 轻量重排（纯计算，对原子记忆二次排序；不调 chat LLM）
        if (query != null
                && context.getAtomicMemories() != null
                && context.getAtomicMemories().size() > 1) {
            context.setAtomicMemories(
                    reranker.rerank(query, context.getAtomicMemories(), budget.atomicTopK));
        }

        // 6. 图谱记忆
        if (query != null) {
            context.setRelatedEntities(graphMemory.search(userId, query));
        }

        return context;
    }

    /** 基于向量的语义检索 */
    public List<MemoryAtom> retrieveByVector(Long userId, float[] queryEmbedding, int topK) {
        return atomMemoryEngine.searchByVector(userId, queryEmbedding, topK);
    }

    /** 混合检索（时序 + 语义 + 标签） */
    public List<MemoryAtom> retrieveHybrid(
            Long userId, float[] queryEmbedding, Instant timeStart, Instant timeEnd, int topK) {
        var query = new HybridQuery(userId, queryEmbedding, timeStart, timeEnd, null, null, topK);
        return atomMemoryEngine.searchHybrid(query);
    }

    /** Bundle Search：图路由证据链检索 */
    public List<MemoryBundle> retrieveBundles(
            Long userId, float[] queryEmbedding, int topK, Instant queryTime) {
        return atomMemoryEngine.searchBundles(userId, queryEmbedding, topK, queryTime);
    }

    /** 按任务类型检索程序化记忆（TODO: ProceduralMemory 未实现） */
    public List<Object> retrieveProcedural(Long userId, String taskType) {
        // TODO: 待 ProceduralMemory Repository 完善后实现
        return List.of();
    }

    // ===== 查询意图路由（对齐认知心理学：线索类型决定激活模式） =====

    /** 查询意图分类（轻量规则，不调 LLM）。 认知心理学：不同线索类型激活不同记忆系统—— 时间线索→情景记忆，语义线索→长期记忆，"怎么做"→程序化记忆。 */
    private QueryIntent classifyIntent(String query) {
        if (query == null || query.isBlank()) return QueryIntent.GENERAL;

        var lower = query.toLowerCase();
        // 程序化意图：怎么做/步骤/流程/方法
        if (lower.contains("怎么")
                || lower.contains("如何")
                || lower.contains("步骤")
                || lower.contains("流程")
                || lower.contains("方法")
                || lower.contains("how to")) {
            return QueryIntent.PROCEDURAL;
        }
        // 时序意图：什么时候/上次/之前/昨天
        if (lower.contains("什么时候")
                || lower.contains("上次")
                || lower.contains("之前")
                || lower.contains("昨天")
                || lower.contains("上周")
                || lower.contains("when")) {
            return QueryIntent.TEMPORAL;
        }
        // 因果意图：为什么/原因/因为
        if (lower.contains("为什么") || lower.contains("原因") || lower.contains("why")) {
            return QueryIntent.CAUSAL;
        }
        return QueryIntent.GENERAL;
    }

    /** 预算分配（借鉴 M-FLOW 软路由）。 根据意图动态调整各类记忆的检索数量。 */
    private RetrievalBudget allocateBudget(QueryIntent intent) {
        return switch (intent) {
            case PROCEDURAL -> new RetrievalBudget(3, 2, 5); // 程序化优先
            case TEMPORAL -> new RetrievalBudget(3, 6, 1); // 情景优先
            case CAUSAL -> new RetrievalBudget(4, 5, 1); // 情景+原子
            case GENERAL -> new RetrievalBudget(ATOMIC_TOP_K, EPISODIC_TOP_K, PROCEDURAL_TOP_K);
        };
    }

    /** 查询意图类型 */
    private enum QueryIntent {
        GENERAL,
        PROCEDURAL,
        TEMPORAL,
        CAUSAL
    }

    /** 检索预算分配 */
    private record RetrievalBudget(int atomicTopK, int episodicTopK, int proceduralTopK) {}

    /** 记忆上下文（分区聚合结果） */
    @lombok.Getter
    @lombok.Setter
    public static class MemoryContext {
        /** 短期记忆：当前对话上下文 */
        private List<MemoryMessage> recentMessages = new ArrayList<>();

        /** 原子记忆：语义相关的长期记忆片段（已重排） */
        private List<MemoryAtom> atomicMemories = new ArrayList<>();

        /** 情景 Bundle：关联证据链 */
        private List<MemoryBundle> episodicBundles = new ArrayList<>();

        /** 程序化记忆：scope='procedural' 的原子记忆 */
        private List<MemoryAtom> proceduralMemories = new ArrayList<>();

        /** 图谱记忆实体 */
        private List<GraphMemoryNode> relatedEntities = new ArrayList<>();
    }
}
