package com.xuejiai.aaf.module.system.role.relation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 授予资源关系请求。
 *
 * @author AaronZZH & Kiro
 */
public record GrantRelationDTO(
        @NotBlank @Schema(description = "资源类型") String resourceType,
        @NotNull @Schema(description = "资源 ID") Long resourceId,
        @NotBlank @Schema(description = "关系类型: OWNER/EDITOR/VIEWER") String relation,
        @NotBlank @Schema(description = "主体类型: USER/ROLE/AGENT") String subjectType,
        @NotNull @Schema(description = "主体 ID") Long subjectId) {}
