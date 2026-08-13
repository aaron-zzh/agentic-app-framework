package com.xuejiai.aaf.module.system.org.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 工作区响应。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "工作区信息")
public record WorkspaceVO(
        @Schema(description = "工作区 ID") Long id,
        @Schema(description = "所属组织 ID") Long orgId,
        @Schema(description = "工作区名称") String name,
        @Schema(description = "工作区标识（组织内唯一）") String slug,
        @Schema(description = "工作区管理者用户 ID") Long ownerId,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
