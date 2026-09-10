package com.xuejiai.aaf.module.billing.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreditRedeemCodePageParam extends PageParam {
    private String status;
    private String type;
    private String batchType;
    private Long skuId;
    private String skuCode;
    private Long redeemedByUserId;
}
