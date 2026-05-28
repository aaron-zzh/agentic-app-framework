package com.xuejiai.aaf.module.knowledge.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** 知识库文档信息 Response VO。 */
@Schema(description = "知识库文档信息")
public record KnowledgeDocumentVO(
        @Schema(description = "文档编号") Long id,
        @Schema(description = "所属知识库编号") Long knowledgeBaseId,
        @Schema(description = "文档标题") String title,
        @Schema(description = "文件路径") String filePath,
        @Schema(description = "文件类型", example = "pdf") String fileType,
        @Schema(description = "文件大小（字节）") Long fileSize,
        @Schema(description = "内容哈希") String contentHash,
        @Schema(description = "状态（0=待处理 1=处理中 2=已完成 3=失败）") Integer status,
        @Schema(description = "错误信息") String errorMessage,
        @Schema(description = "分块数量") Integer chunkCount,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
