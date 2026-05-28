package com.xuejiai.aaf.module.system.role.relation.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 资源关系响应。
 *
 * @author AaronZZH & Kiro
 */
public record ResourceRelationVO(
        @Schema(description = "主键 ID") Long id,
        @Schema(description = "资源类型") String resourceType,
        @Schema(description = "资源 ID") Long resourceId,
        @Schema(description = "关系类型") String relation,
        @Schema(description = "主体类型") String subjectType,
        @Schema(description = "主体 ID") Long subjectId,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
