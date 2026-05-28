package com.xuejiai.aaf.module.system.org.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 组织响应。
 *
 * @author AaronZZH & Kiro
 */
public record OrganizationVO(
        @Schema(description = "主键 ID") Long id,
        String name,
        String slug,
        String type,
        Long ownerId,
        LocalDateTime createTime) {}
