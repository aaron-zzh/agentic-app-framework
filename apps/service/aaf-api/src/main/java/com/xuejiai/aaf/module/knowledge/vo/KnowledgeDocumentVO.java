package com.xuejiai.aaf.module.knowledge.vo;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 知识来源文档信息。
 *
 * @param summary 文档摘要：系统根据文档内容自动生成，只读；尚未生成时为 null
 */
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
        Long sourceDocumentVersionId,
        String title,
        String summary,
        String fileType,
        Long fileSize,
        String contentHash,
        Integer status,
        String errorMessage,
        Integer chunkCount,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
