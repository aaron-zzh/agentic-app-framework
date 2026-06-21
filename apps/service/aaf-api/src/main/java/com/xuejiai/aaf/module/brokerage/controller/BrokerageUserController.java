package com.xuejiai.aaf.module.brokerage.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageUser;
import com.xuejiai.aaf.module.brokerage.service.BrokerageUserCrudService;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageUserDTO;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageUserPageParam;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageUserVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 分销员管理接口。 */
@Tag(name = "分销员管理")
@RestController
@RequestMapping("/api/brokerage/users")
@RequiredArgsConstructor
public class BrokerageUserController
        extends BaseCrudController<
                BrokerageUser,
                BrokerageUserVO,
                BrokerageUserDTO,
                BrokerageUserDTO,
                BrokerageUserPageParam> {

    private final BrokerageUserCrudService brokerageUserCrudService;

    @Override
    protected BrokerageUserCrudService getService() {
        return brokerageUserCrudService;
    }
}
