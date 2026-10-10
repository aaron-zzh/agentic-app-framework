package com.xuejiai.aaf.module.todo.vo;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** 待办协作者：通过分享获得对象级访问关系的用户。 */
@Schema(description = "待办协作者")
public record TodoCollaboratorVO(
        @Schema(description = "协作者用户 ID") Long userId,
        @Schema(description = "对象级关系：OWNER / EDITOR / VIEWER") String relation,
        @Schema(description = "关系过期时间；为空表示长期有效") Instant expiresAt) {}
