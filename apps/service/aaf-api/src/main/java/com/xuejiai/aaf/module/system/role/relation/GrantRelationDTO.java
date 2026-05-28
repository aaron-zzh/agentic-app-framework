package com.xuejiai.aaf.module.system.role.relation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 授予/撤销资源关系 Request DTO。 */
@Schema(description = "授予资源关系请求")
public record GrantRelationDTO(
        @Schema(description = "资源类型", example = "document") @NotBlank String resourceType,
        @Schema(description = "资源 ID", example = "42") @NotNull Long resourceId,
        @Schema(description = "关系类型", example = "EDITOR") @NotBlank String relation,
        @Schema(description = "主体类型", example = "USER") @NotBlank String subjectType,
        @Schema(description = "主体 ID", example = "1") @NotNull Long subjectId) {}
