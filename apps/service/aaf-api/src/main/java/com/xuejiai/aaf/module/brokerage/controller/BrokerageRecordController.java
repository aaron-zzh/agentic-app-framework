package com.xuejiai.aaf.module.brokerage.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageRecord;
import com.xuejiai.aaf.module.brokerage.service.BrokerageRecordCrudService;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageRecordPageParam;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageRecordVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 佣金流水查询接口（只读）。 */
@Tag(name = "佣金流水")
@RestController
@RequestMapping("/api/brokerage/records")
@RequiredArgsConstructor
public class BrokerageRecordController
        extends BaseCrudController<
                BrokerageRecord, BrokerageRecordVO, Void, Void, BrokerageRecordPageParam> {

    private final BrokerageRecordCrudService brokerageRecordCrudService;

    @Override
    protected BrokerageRecordCrudService getService() {
        return brokerageRecordCrudService;
    }
}
