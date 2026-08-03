package com.xuejiai.aaf.module.billing.vo;

import jakarta.validation.constraints.NotBlank;

/** 管理员为指定用户开通或升级会员请求。 */
public record AdminSubscriptionDTO(@NotBlank String planCode) {}
