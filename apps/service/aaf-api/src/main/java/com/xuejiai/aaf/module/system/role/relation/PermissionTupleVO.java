package com.xuejiai.aaf.module.system.role.relation;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** ReBAC 关系元组响应。 */
@Schema(description = "ReBAC 关系元组")
public record PermissionTupleVO(
        Long id,
        String objectType,
        String objectId,
        String relation,
        String subjectType,
        String subjectId,
        String subjectRelation,
        Instant expiresAt) {}
