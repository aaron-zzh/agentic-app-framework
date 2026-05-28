package com.xuejiai.aaf.module.system.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户简要响应（列表/下拉选择场景）
 *
 * @author AaronZZH & Kiro
 */
public record UserSimpleVO(
        @Schema(description = "用户 ID") Long id,
        @Schema(description = "用户名") String username,
        @Schema(description = "昵称") String nickname) {}
