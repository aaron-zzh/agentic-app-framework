package com.xuejiai.aaf.module.billing.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubscriptionSkuPageParam extends PageParam {
    private Long planId;
    private String skuCode;
    private String billingCycle;
    private String status;
}
