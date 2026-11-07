package com.xuejiai.aaf.module.knowledge.vo;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 知识库信息。
 *
 * @param description 知识库说明：用户填写，系统永不修改
 * @param aiSummary 涵盖主题：系统根据已入库文档自动生成，只读；尚未生成时为 null
 * @param aiSummaryUpdatedAt 涵盖主题最近生成时间，只读；尚未生成时为 null
 */
public record KnowledgeBaseVO(
        Long id,
        UUID stableId,
        String name,
        String description,
        String aiSummary,
        LocalDateTime aiSummaryUpdatedAt,
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
