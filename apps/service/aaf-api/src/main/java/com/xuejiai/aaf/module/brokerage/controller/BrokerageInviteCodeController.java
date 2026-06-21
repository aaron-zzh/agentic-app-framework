package com.xuejiai.aaf.module.brokerage.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageInviteCode;
import com.xuejiai.aaf.module.brokerage.service.BrokerageInviteCodeCrudService;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageInviteCodeDTO;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageInviteCodePageParam;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageInviteCodeVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 邀请码管理接口。 */
@Tag(name = "邀请码管理")
@RestController
@RequestMapping("/api/brokerage/invite-codes")
@RequiredArgsConstructor
public class BrokerageInviteCodeController
        extends BaseCrudController<
                BrokerageInviteCode,
                BrokerageInviteCodeVO,
                BrokerageInviteCodeDTO,
                BrokerageInviteCodeDTO,
                BrokerageInviteCodePageParam> {

    private final BrokerageInviteCodeCrudService inviteCodeCrudService;

    @Override
    protected BrokerageInviteCodeCrudService getService() {
        return inviteCodeCrudService;
    }
}
