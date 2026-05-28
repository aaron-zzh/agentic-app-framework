package com.xuejiai.aaf.module.intelligent.memory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** 记忆原子 Response VO。 */
@Schema(description = "记忆原子信息")
public record MemoryAtomVO(
        @Schema(description = "记忆 ID") UUID id,
        @Schema(description = "所属用户 ID") Long userId,
        @Schema(description = "范围（short_term/long_term/episodic/procedural）") String scope,
        @Schema(description = "内容") String content,
        @Schema(description = "事件时间") Instant eventTime,
        @Schema(description = "权重") Double weight,
        @Schema(description = "访问次数") Integer accessCount,
        @Schema(description = "最后访问时间") Instant lastAccessedAt,
        @Schema(description = "标签") List<String> tags,
        @Schema(description = "元数据") Map<String, Object> metadata,
        @Schema(description = "创建时间") Instant createdAt) {}
