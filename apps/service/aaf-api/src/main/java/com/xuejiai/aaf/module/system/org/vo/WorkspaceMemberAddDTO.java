package com.xuejiai.aaf.module.system.org.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 添加工作区成员请求。
 *
 * @author AaronZZH & Kiro
 */
public record WorkspaceMemberAddDTO(
        @NotNull(message = "用户 ID 不能为空") @Schema(description = "用户 ID") Long userId) {}
