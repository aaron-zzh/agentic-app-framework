package com.xuejiai.aaf.module.system.user.vo;

import jakarta.validation.constraints.NotNull;

/** 修改用户状态请求。 */
public record UserUpdateStatusDTO(@NotNull(message = "状态不能为空") Integer status) {}
