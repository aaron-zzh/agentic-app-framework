package com.xuejiai.aaf.module.system.role.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 数据权限规则响应。
 *
 * @author AaronZZH & Kiro
 */
public record DataAccessRuleVO(
        @Schema(description = "主键 ID") Long id,
        @Schema(description = "实体标识") String entitySlug,
        String roles,
        String condition,
        String effect,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
