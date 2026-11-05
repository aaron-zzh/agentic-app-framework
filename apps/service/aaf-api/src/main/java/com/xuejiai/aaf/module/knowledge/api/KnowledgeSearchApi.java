package com.xuejiai.aaf.module.knowledge.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 知识模块对业务域暴露的受控文档检索接口。 */
public interface KnowledgeSearchApi {

    /** 解析知识文档引用；不存在或已删除时返回空。 */
    Optional<DocumentReference> findDocument(Long documentId);

    /** 复用知识库授权与混合检索链路，并限定知识库和文档 stable ID。 */
    SearchResult search(SearchQuery query);

    /** 按 stable_id 批量解析知识库概要（名称、描述），不存在或已删除时跳过。 */
    List<KnowledgeBaseSummary> findKnowledgeBases(Set<UUID> stableIds);

    record DocumentReference(
            Long id, UUID stableId, UUID knowledgeBaseStableId, String title, Integer status) {}

    record SearchQuery(
            String query,
            Set<UUID> knowledgeBaseIds,
            Set<UUID> documentIds,
            int topK,
            double threshold) {}

    record SearchResult(List<SearchHit> hits, Set<String> degradedChannels) {}

    record SearchHit(String content, double score, Source source) {}

    record Source(UUID knowledgeBaseId, UUID documentId, String sourceKey, String sourceUri) {}

    record KnowledgeBaseSummary(UUID stableId, String name, String description) {}
}
