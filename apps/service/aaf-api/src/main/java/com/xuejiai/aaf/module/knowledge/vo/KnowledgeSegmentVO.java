package com.xuejiai.aaf.module.knowledge.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 知识库段落响应 VO。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "知识库段落信息")
public record KnowledgeSegmentVO(
        @Schema(description = "段落编号") Long id,
        @Schema(description = "所属文档编号") Long documentId,
        @Schema(description = "所属知识库编号") Long knowledgeBaseId,
        @Schema(description = "段落内容") String content,
        @Schema(description = "段落位置") Integer position,
        @Schema(description = "字数") Integer wordCount,
        @Schema(description = "是否启用") Boolean enabled,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
