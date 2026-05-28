package com.xuejiai.aaf.module.system.log.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录日志响应。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "登录日志")
public record LoginLogVO(
        @Schema(description = "ID") Long id,
        @Schema(description = "用户 ID") Long userId,
        @Schema(description = "用户名") String username,
        @Schema(description = "登录类型：PASSWORD/EMAIL/OAUTH") String loginType,
        @Schema(description = "登录 IP") String ip,
        @Schema(description = "User-Agent") String userAgent,
        @Schema(description = "IP 归属地") String location,
        @Schema(description = "是否成功") Boolean success,
        @Schema(description = "失败原因") String failReason,
        @Schema(description = "登录时间") LocalDateTime loginTime) {}
