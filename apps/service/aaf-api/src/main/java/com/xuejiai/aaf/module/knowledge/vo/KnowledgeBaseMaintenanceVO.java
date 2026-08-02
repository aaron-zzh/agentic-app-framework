package com.xuejiai.aaf.module.knowledge.vo;

import java.time.LocalDateTime;
import java.util.UUID;

/** 知识库后台运维视图。 */
public record KnowledgeBaseMaintenanceVO(
        Long id,
        UUID stableId,
        Long orgId,
        Long workspaceId,
        Long ownerId,
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
