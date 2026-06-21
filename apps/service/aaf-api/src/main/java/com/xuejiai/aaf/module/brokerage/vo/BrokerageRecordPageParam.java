package com.xuejiai.aaf.module.brokerage.vo;

import com.xuejiai.aaf.common.enums.brokerage.BrokerageRecordStatusEnum;
import com.xuejiai.aaf.common.model.PageParam;

import lombok.Getter;
import lombok.Setter;

/** 佣金流水分页查询参数。 */
@Getter
@Setter
public class BrokerageRecordPageParam extends PageParam {

    private Long contactId;
    private String bizType;
    private BrokerageRecordStatusEnum status;
}
