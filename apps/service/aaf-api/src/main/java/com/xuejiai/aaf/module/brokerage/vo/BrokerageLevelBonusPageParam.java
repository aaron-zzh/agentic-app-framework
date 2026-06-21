package com.xuejiai.aaf.module.brokerage.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Getter;
import lombok.Setter;

/** 会员等级佣金加成分页查询参数。 */
@Getter
@Setter
public class BrokerageLevelBonusPageParam extends PageParam {

    private Long ruleId;
    private Long planId;
}
