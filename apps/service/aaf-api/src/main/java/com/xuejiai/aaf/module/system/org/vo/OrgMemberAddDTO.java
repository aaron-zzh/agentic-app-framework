package com.xuejiai.aaf.module.system.org.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 添加组织成员请求。 */
public record OrgMemberAddDTO(
        @NotNull(message = "用户 ID 不能为空") Long userId, @NotBlank(message = "角色不能为空") String role) {}
