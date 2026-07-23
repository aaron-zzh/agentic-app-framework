package com.xuejiai.aaf.module.billing.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubscriptionPlanPageParam extends PageParam {
    private String keyword;
    private String status;
}
