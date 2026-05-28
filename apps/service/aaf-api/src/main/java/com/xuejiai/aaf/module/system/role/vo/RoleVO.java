package com.xuejiai.aaf.module.system.role.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 角色响应。
 *
 * @author AaronZZH & Kiro
 */
public record RoleVO(
        @Schema(description = "主键 ID") Long id,
        @Schema(description = "编码") String code,
        @Schema(description = "名称") String name,
        @Schema(description = "描述") String description,
        @Schema(description = "状态") Integer status,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
