package com.xuejiai.aaf.module.system.org.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 工作区成员响应。
 *
 * @author AaronZZH & Kiro
 */
public record WorkspaceMemberVO(
        @Schema(description = "主键 ID") Long id,
        @Schema(description = "工作区 ID") Long workspaceId,
        @Schema(description = "用户 ID") Long userId,
        @Schema(description = "加入时间") LocalDateTime createTime) {}
