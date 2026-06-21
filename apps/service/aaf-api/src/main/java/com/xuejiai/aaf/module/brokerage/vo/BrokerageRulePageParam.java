package com.xuejiai.aaf.module.brokerage.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Getter;
import lombok.Setter;

/** 佣金规则分页查询参数。 */
@Getter
@Setter
public class BrokerageRulePageParam extends PageParam {

    private String bizType;
    private String status;
}
