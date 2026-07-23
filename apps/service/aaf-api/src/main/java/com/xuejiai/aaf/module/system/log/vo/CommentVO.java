package com.xuejiai.aaf.module.system.log.vo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/** 评论响应。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "评论信息")
public record CommentVO(
        @Schema(description = "评论 ID") Long id,
        @Schema(description = "关联实体类型") String entityType,
        @Schema(description = "关联实体 ID") Long entityId,
        @Schema(description = "评论内容") String content,
        @Schema(description = "@mentions 用户 ID 列表") String mentions,
        @Schema(description = "评论归属用户 ID") Long ownerId,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
