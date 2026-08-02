package com.xuejiai.aaf.module.knowledge.vo;

import java.time.LocalDateTime;
import java.util.UUID;

/** 知识来源文档信息。 */
public record KnowledgeDocumentVO(
        Long id,
        UUID stableId,
        Long knowledgeBaseId,
        Long sourceDocumentId,
        Long uploadedBy,
        String sourceType,
        String sourceKey,
        String sourceUri,
        UUID activeRunId,
        String title,
        String fileType,
        Long fileSize,
        String contentHash,
        Integer status,
        String errorMessage,
        Integer chunkCount,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
