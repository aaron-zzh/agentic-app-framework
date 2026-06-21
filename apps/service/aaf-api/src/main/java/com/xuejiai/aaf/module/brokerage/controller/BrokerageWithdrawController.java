package com.xuejiai.aaf.module.brokerage.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageWithdraw;
import com.xuejiai.aaf.module.brokerage.service.BrokerageWithdrawCrudService;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageWithdrawDTO;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageWithdrawPageParam;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageWithdrawVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 佣金提现管理接口。 */
@Tag(name = "佣金提现管理")
@RestController
@RequestMapping("/api/brokerage/withdraws")
@RequiredArgsConstructor
public class BrokerageWithdrawController
        extends BaseCrudController<
                BrokerageWithdraw,
                BrokerageWithdrawVO,
                BrokerageWithdrawDTO,
                BrokerageWithdrawDTO,
                BrokerageWithdrawPageParam> {

    private final BrokerageWithdrawCrudService brokerageWithdrawCrudService;

    @Override
    protected BrokerageWithdrawCrudService getService() {
        return brokerageWithdrawCrudService;
    }
}
