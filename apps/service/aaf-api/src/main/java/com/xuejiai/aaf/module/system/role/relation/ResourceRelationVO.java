package com.xuejiai.aaf.module.system.role.relation;

import io.swagger.v3.oas.annotations.media.Schema;

/** 资源关系 Response VO。 */
@Schema(description = "资源关系信息")
public record ResourceRelationVO(
        @Schema(description = "编号") Long id,
        @Schema(description = "资源类型") String resourceType,
        @Schema(description = "资源 ID") Long resourceId,
        @Schema(description = "关系类型") String relation,
        @Schema(description = "主体类型") String subjectType,
        @Schema(description = "主体 ID") Long subjectId) {}
