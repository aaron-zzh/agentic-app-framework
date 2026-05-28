package com.xuejiai.aaf.module.system.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 修改用户状态请求。
 *
 * @author AaronZZH & Kiro
 */
public record UserUpdateStatusDTO(
        @Schema(description = "状态（0 正常 / 1 禁用）", example = "0") @NotNull(message = "状态不能为空")
                Integer status) {}
