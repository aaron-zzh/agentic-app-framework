package com.xuejiai.aaf.module.brokerage.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Getter;
import lombok.Setter;

/** 分销员分页查询参数。 */
@Getter
@Setter
public class BrokerageUserPageParam extends PageParam {

    private Long contactId;
    private Long referrerContactId;
    private Boolean brokerageEnabled;
}
