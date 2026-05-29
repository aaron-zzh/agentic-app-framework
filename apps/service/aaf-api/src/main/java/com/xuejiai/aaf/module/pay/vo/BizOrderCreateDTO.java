package com.xuejiai.aaf.module.pay.vo;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 创建业务订单请求 */
public record BizOrderCreateDTO(
        @NotBlank String orderType,
        @NotBlank String subject,
        @NotNull @Min(1) Long totalAmount,
        @NotBlank String channelCode,
        List<BizOrderItemCreateDTO> items) {

    /** 无明细行的简化构造（充值等场景） */
    public BizOrderCreateDTO(String orderType, String subject, Long totalAmount, String channelCode) {
        this(orderType, subject, totalAmount, channelCode, null);
    }
}
