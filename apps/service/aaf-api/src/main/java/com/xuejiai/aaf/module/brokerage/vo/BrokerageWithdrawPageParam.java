package com.xuejiai.aaf.module.brokerage.vo;

import com.xuejiai.aaf.common.enums.brokerage.BrokerageWithdrawStatusEnum;
import com.xuejiai.aaf.common.enums.brokerage.BrokerageWithdrawTypeEnum;
import com.xuejiai.aaf.common.model.PageParam;

import lombok.Getter;
import lombok.Setter;

/** 佣金提现分页查询参数。 */
@Getter
@Setter
public class BrokerageWithdrawPageParam extends PageParam {

    private Long contactId;
    private BrokerageWithdrawStatusEnum status;
    private BrokerageWithdrawTypeEnum type;
}
