package com.xuejiai.aaf.module.ai.memory;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Cognition 记忆记录 Response VO。 */
@Schema(description = "记忆记录信息")
public record MemoryRecordVO(
        @Schema(description = "记忆 ID") String id,
        @Schema(description = "范围（short_term/long_term/episodic/procedural）") String scope,
        @Schema(description = "内容") String content,
        @Schema(description = "脱敏摘要") String redactedSummary,
        @Schema(description = "重要性") double importance,
        @Schema(description = "置信度") double confidence,
        @Schema(description = "隐私等级") String privacy,
        @Schema(description = "标签") List<String> tags,
        @Schema(description = "过期时间") Instant expiresAt,
        @Schema(description = "创建时间") Instant createdAt) {}
