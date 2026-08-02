package com.xuejiai.aaf.module.knowledge.vo;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 授权多库搜索响应。 */
public record KnowledgeSearchResponseVO(
        List<SearchResultItemVO> results,
        Set<UUID> searchedKnowledgeBaseIds,
        Set<String> degradedChannels) {

    public record SearchResultItemVO(
            String candidateKey,
            String content,
            double score,
            Set<String> matchedChannels,
            SourceVO source) {}

    public record SourceVO(
            UUID knowledgeBaseId,
            String knowledgeBaseName,
            String visibility,
            UUID documentId,
            String sourceType,
            String sourceKey,
            String sourceUri,
            UUID runId,
            UUID focusChunkId,
            Set<UUID> factIds,
            Set<UUID> evidenceIds) {}
}
