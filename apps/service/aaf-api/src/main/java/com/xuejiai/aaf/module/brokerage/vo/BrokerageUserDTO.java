package com.xuejiai.aaf.module.brokerage.vo;

import jakarta.validation.constraints.NotNull;

/** 分销员创建/更新 DTO。 */
public record BrokerageUserDTO(
        @NotNull Long contactId, Long referrerContactId, Boolean brokerageEnabled) {}
