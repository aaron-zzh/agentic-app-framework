package com.xuejiai.aaf.module.system.org.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 组织成员响应。
 *
 * @author AaronZZH & Kiro
 */
public record OrgMemberVO(
        @Schema(description = "主键 ID") Long id,
        Long orgId,
        Long userId,
        String role,
        LocalDateTime createTime) {}
