package com.xuejiai.aaf.module.brokerage.vo;

import jakarta.validation.constraints.NotNull;

/** 邀请码创建/更新 DTO。 */
public record BrokerageInviteCodeDTO(@NotNull Long contactId, String channel) {}
