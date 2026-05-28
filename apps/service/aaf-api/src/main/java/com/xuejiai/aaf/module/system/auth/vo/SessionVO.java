package com.xuejiai.aaf.module.system.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户会话信息。
 *
 * @author AaronZZH & Kiro
 */
public record SessionVO(
        @Schema(description = "设备 ID") String deviceId,
        @Schema(description = "登录时间") String loginTime) {}
