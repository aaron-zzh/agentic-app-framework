package com.xuejiai.aaf.module.brokerage.repository;

import java.util.List;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageRule;

public interface BrokerageRuleRepository extends CrudEntityRepository<BrokerageRule> {

    /** 按业务类型查询启用规则，优先级升序（数字越小越优先） */
    List<BrokerageRule> findByBizTypeAndStatusOrderByPriorityAsc(String bizType, String status);
}
