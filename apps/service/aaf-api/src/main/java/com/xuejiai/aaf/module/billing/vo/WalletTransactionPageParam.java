package com.xuejiai.aaf.module.billing.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WalletTransactionPageParam extends PageParam {
    private Long accountId;
    private String type;
    private String category;
    private String batchType;
}
