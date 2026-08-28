package com.xuejiai.aaf.framework.intelligent.cognition.port;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.Hit;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationSubject;

/**
 * 混合检索统一门面：跨记忆与知识库的授权编排入口。
 *
 * <p>职责：意图分类、跨通道预算切分、并行调用 {@code MemoryRetrievalPort} 与知识库检索、加权 RRF
 * 融合、融合后重排。通道本身（记忆、知识库）各自实现单一来源检索，不做跨源融合；本门面不重新实现检索算法，只做编排。
 *
 * <p>唯一目标态门面，见 retrieval.md「双层架构」。上层只应经 {@code L1ContextPort} 触达本门面，不直接选择存储引擎。
 */
public interface UnifiedRetrievalPort {

    /**
     * 执行一次跨源融合检索。
     *
     * @param request 检索请求
     * @return 融合并重排后的跨源候选
     */
    UnifiedRetrievalResult retrieve(UnifiedRetrievalRequest request);

    /**
     * 跨源检索请求。
     *
     * @param subject 授权主体（知识库检索必须携带已解析主体，不得使用 unresolved）
     * @param userId 记忆检索所属用户；为空则跳过记忆通道
     * @param query 查询文本
     * @param knowledgeBaseIds 已授权知识库范围
     * @param includePublic 是否纳入公开知识库
     * @param maxItems 融合后返回的最大条数，对应上游剩余预算
     */
    record UnifiedRetrievalRequest(
            AuthorizationSubject subject,
            Long userId,
            String query,
            Set<UUID> knowledgeBaseIds,
            boolean includePublic,
            int maxItems) {
        public UnifiedRetrievalRequest {
            Objects.requireNonNull(subject, "subject 不能为空");
            query = Objects.requireNonNullElse(query, "").trim();
            knowledgeBaseIds = knowledgeBaseIds == null ? Set.of() : Set.copyOf(knowledgeBaseIds);
            if (maxItems <= 0) {
                throw new IllegalArgumentException("maxItems 必须大于零");
            }
        }
    }

    /**
     * 跨源融合结果。
     *
     * @param fused 融合并重排后的跨源候选，长度不超过 {@code maxItems}
     * @param knowledgeHits 知识库原始命中，供来源引用使用
     */
    record UnifiedRetrievalResult(List<FusedCandidate> fused, List<Hit> knowledgeHits) {
        public UnifiedRetrievalResult {
            fused = fused == null ? List.of() : List.copyOf(fused);
            knowledgeHits = knowledgeHits == null ? List.of() : List.copyOf(knowledgeHits);
        }

        public static UnifiedRetrievalResult empty() {
            return new UnifiedRetrievalResult(List.of(), List.of());
        }
    }

    /**
     * 融合后的跨源候选。
     *
     * @param candidateKey 来源内稳定键，跨源去重依据
     * @param content 候选内容（记忆为原文，知识为片段正文）
     * @param channel 命中通道：atomic / episodic / procedural / knowledge
     * @param score 融合分（RRF），重排只改变顺序不回填该分值，保留原始融合分作为审计依据
     */
    record FusedCandidate(String candidateKey, String content, String channel, double score) {
        /** 返回替换融合分后的新实例；用于融合阶段回填分值。 */
        public FusedCandidate withScore(double newScore) {
            return new FusedCandidate(candidateKey, content, channel, newScore);
        }
    }
}
