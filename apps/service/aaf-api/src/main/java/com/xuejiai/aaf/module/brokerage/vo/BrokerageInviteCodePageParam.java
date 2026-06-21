package com.xuejiai.aaf.module.brokerage.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Getter;
import lombok.Setter;

/** 邀请码分页查询参数。 */
@Getter
@Setter
public class BrokerageInviteCodePageParam extends PageParam {

    private Long contactId;
    private String channel;
}
