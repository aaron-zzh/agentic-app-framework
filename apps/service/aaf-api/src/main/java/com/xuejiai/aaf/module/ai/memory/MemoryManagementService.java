/**
 * 记忆管理 Service。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.ai.memory;

import java.util.List;
import java.util.UUID;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingService;
import com.xuejiai.aaf.framework.engine.memory.AtomMemoryEngine;
import com.xuejiai.aaf.framework.engine.memory.MemoryAtom;
import com.xuejiai.aaf.framework.engine.memory.MemoryAtomRepository;
import com.xuejiai.aaf.framework.intelligent.cognition.memory.MemoryRerankerService;
import com.xuejiai.aaf.framework.intelligent.cognition.memory.MemoryRetrievalService;
import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemoryManagementService {

    private final MemoryAtomRepository repository;
    private final AtomMemoryEngine memoryEngine;
    private final EmbeddingService embeddingService;
    private final MemoryRetrievalService retrievalService;
    private final MemoryRerankerService reranker;
    private final OperatorContext operatorContext;

    /**
     * 分页查询当前用户的记忆列表
     *
     * @param scope 范围筛选（可选）
     * @param pageable 分页参数
     * @return 记忆分页结果
     */
    public PageResult<MemoryAtomVO> list(String scope, Pageable pageable) {
        var userId = operatorContext.currentUserId().orElseThrow();
        Page<MemoryAtom> page;
        if (scope != null) {
            page = repository.findByUserIdAndScope(userId, scope, pageable);
        } else {
            page = repository.findByUserId(userId, pageable);
        }
        return new PageResult<>(page.map(this::toVO).toList(), page.getTotalElements());
    }

    /**
     * 搜索记忆（关键词匹配）
     *
     * @param keyword 关键词
     * @param scope 范围筛选（可选）
     * @return 匹配的记忆列表
     */
    public List<MemoryAtomVO> search(String keyword, String scope) {
        var userId = operatorContext.currentUserId().orElseThrow();
        List<MemoryAtom> results;
        if (scope != null) {
            results = repository.findByUserIdAndScopeAndContentContaining(userId, scope, keyword);
        } else {
            results = repository.findByUserIdAndContentContaining(userId, keyword);
        }
        return results.stream().map(this::toVO).toList();
    }

    /**
     * 显式记住一条记忆（对齐 m_flow add）。当前用户主动"记住"某事时调用。
     *
     * @param content 记忆内容
     * @param scope 范围（默认 long_term）
     * @return 写入的记忆
     */
    @Transactional
    public MemoryAtomVO add(String content, String scope) {
        var userId = operatorContext.currentUserId().orElseThrow();
        var atom = new MemoryAtom();
        atom.setUserId(userId);
        atom.setScope(scope != null ? scope : "long_term");
        atom.setContent(content);
        atom.setEmbedding(embeddingService.embed(content));
        atom.setEventTime(Instant.now());
        atom.setWeight(0.6);
        return toVO(memoryEngine.store(atom));
    }

    /**
     * 语义检索记忆（对齐 m_flow search）。走认知检索（意图路由 + 轻量重排），返回相关记忆上下文。
     *
     * @param query 自然语言查询
     * @param topK 返回数量（默认 8）
     * @return 按相关性排序的记忆
     */
    public List<MemoryAtomVO> semanticSearch(String query, Integer topK) {
        var userId = operatorContext.currentUserId().orElseThrow();
        int limit = topK != null && topK > 0 ? topK : 8;
        // 显式检索属高价值、非延迟敏感场景：宽召回 + 专用重排模型（带门控 + 失败降级）
        var candidates =
                retrievalService.retrieveByVector(
                        userId, embeddingService.embed(query), Math.max(limit * 3, 20));
        return reranker.rerank(query, candidates, limit, MemoryRerankerService.Mode.RERANK_MODEL)
                .stream()
                .map(this::toVO)
                .toList();
    }

    /**
     * 删除指定记忆
     *
     * @param ids 记忆 ID 列表
     */
    @Transactional
    public void delete(List<UUID> ids) {
        memoryEngine.delete(ids);
    }

    /**
     * 清空当前用户指定范围的记忆
     *
     * @param scope 范围（short_term/long_term/episodic/procedural）
     */
    @Transactional
    public void clearByScope(String scope) {
        var userId = operatorContext.currentUserId().orElseThrow();
        var atoms = repository.findByUserIdAndScope(userId, scope);
        if (!atoms.isEmpty()) {
            memoryEngine.delete(atoms.stream().map(MemoryAtom::getId).toList());
        }
    }

    /**
     * 获取记忆统计信息
     *
     * @return 各范围的记忆数量
     */
    public MemoryStatsVO getStats() {
        var userId = operatorContext.currentUserId().orElseThrow();
        long shortTerm = repository.countByUserIdAndScope(userId, "short_term");
        long longTerm = repository.countByUserIdAndScope(userId, "long_term");
        long episodic = repository.countByUserIdAndScope(userId, "episodic");
        long procedural = repository.countByUserIdAndScope(userId, "procedural");
        return new MemoryStatsVO(shortTerm, longTerm, episodic, procedural, shortTerm + longTerm + episodic + procedural);
    }

    private MemoryAtomVO toVO(MemoryAtom e) {
        return new MemoryAtomVO(e.getId(), e.getUserId(), e.getScope(), e.getContent(),
                e.getEventTime(), e.getWeight(), e.getAccessCount(), e.getLastAccessedAt(),
                e.getTags(), e.getMetadata(), e.getCreatedAt());
    }
}
