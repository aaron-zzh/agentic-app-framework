package com.xuejiai.aaf.module.billing.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubscriptionPageParam extends PageParam {
    private Long userId;
    private Long planId;
    private String status;
}
