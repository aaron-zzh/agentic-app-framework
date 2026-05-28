package com.xuejiai.aaf.module.system.org.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 添加组织成员请求。
 *
 * @author AaronZZH & Kiro
 */
public record OrgMemberAddDTO(
        @NotNull(message = "用户 ID 不能为空") @Schema(description = "用户 ID") Long userId,
        @NotBlank(message = "角色不能为空") String role) {}
