package com.xuejiai.aaf.module.brokerage.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageRule;
import com.xuejiai.aaf.module.brokerage.service.BrokerageRuleCrudService;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageRuleDTO;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageRulePageParam;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageRuleVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 佣金规则管理接口。 */
@Tag(name = "佣金规则管理")
@RestController
@RequestMapping("/api/brokerage/rules")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class BrokerageRuleController
        extends BaseCrudController<
                BrokerageRule,
                BrokerageRuleVO,
                BrokerageRuleDTO,
                BrokerageRuleDTO,
                BrokerageRulePageParam> {

    private final BrokerageRuleCrudService brokerageRuleCrudService;

    @Override
    protected BrokerageRuleCrudService getService() {
        return brokerageRuleCrudService;
    }
}
