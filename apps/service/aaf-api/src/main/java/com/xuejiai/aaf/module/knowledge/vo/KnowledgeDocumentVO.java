package com.xuejiai.aaf.module.knowledge.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** 知识库文档响应。 */
@Schema(description = "知识库文档信息")
public record KnowledgeDocumentVO(
        Long id,
        Long knowledgeBaseId,
        String title,
        String filePath,
        String fileType,
        Long fileSize,
        String contentHash,
        Integer status,
        String errorMessage,
        Integer chunkCount,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
