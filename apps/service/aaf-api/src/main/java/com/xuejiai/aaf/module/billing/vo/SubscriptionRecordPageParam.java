package com.xuejiai.aaf.module.billing.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubscriptionRecordPageParam extends PageParam {
    private Long userId;
    private Long planId;
    private Long skuId;
    private String operation;
    private String payStatus;
    private String fulfillmentStatus;
    private String valueStatus;
    private String exceptionCode;
}
