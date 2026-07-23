package com.xuejiai.aaf.module.billing.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EntitlementQuotaPageParam extends PageParam {
    private Long userId;
    private Long entId;
}
