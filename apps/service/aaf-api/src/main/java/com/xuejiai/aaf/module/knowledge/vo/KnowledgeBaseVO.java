package com.xuejiai.aaf.module.knowledge.vo;

import java.time.LocalDateTime;
import java.util.UUID;

/** 知识库信息。 */
public record KnowledgeBaseVO(
        Long id,
        UUID stableId,
        String name,
        String description,
        String visibility,
        String scopeCode,
        String embeddingModel,
        String chunkStrategy,
        Integer chunkSize,
        Integer chunkOverlap,
        long documentCount,
        Integer status,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
