package com.xuejiai.aaf.module.system.role.relation;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 授予/撤销 ReBAC 关系元组请求。 */
@Schema(description = "授予 ReBAC 关系请求")
public record GrantRelationDTO(
        @NotBlank String objectType,
        @NotBlank String objectId,
        @NotBlank String relation,
        @NotBlank String subjectType,
        @NotBlank String subjectId,
        String subjectRelation,
        Instant expiresAt) {}
