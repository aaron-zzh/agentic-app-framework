package com.xuejiai.aaf.module.elder.mall.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.elder.mall.ServiceOrderResource;
import com.xuejiai.aaf.module.elder.mall.domain.ServiceOrder;
import com.xuejiai.aaf.module.elder.mall.service.ServiceOrderService;
import com.xuejiai.aaf.module.elder.mall.vo.ServiceOrderActionDTO;
import com.xuejiai.aaf.module.elder.mall.vo.ServiceOrderCreateDTO;
import com.xuejiai.aaf.module.elder.mall.vo.ServiceOrderPageDTO;
import com.xuejiai.aaf.module.elder.mall.vo.ServiceOrderUpdateDTO;
import com.xuejiai.aaf.module.elder.mall.vo.ServiceOrderVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 服务预约管理接口。 */
@Tag(name = "养老-服务预约")
@RestController
@RequestMapping(ServiceOrderResource.BASE_PATH)
@RequiredArgsConstructor
public class ServiceOrderController
        extends BaseCrudController<
                ServiceOrder,
                ServiceOrderVO,
                ServiceOrderCreateDTO,
                ServiceOrderUpdateDTO,
                ServiceOrderPageDTO> {

    private final ServiceOrderService service;

    @Override
    protected ServiceOrderService getService() {
        return service;
    }

    @Operation(summary = "确认服务预约")
    @PreAuthorize("hasAnyRole('ELDER_OPERATOR', 'ORG_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/actions/confirm")
    public Result<ServiceOrderVO> confirm(@Valid @RequestBody ServiceOrderActionDTO command) {
        return Result.success(service.confirm(command));
    }

    @Operation(summary = "取消服务预约")
    @PreAuthorize("hasAnyRole('ELDER_OPERATOR', 'ORG_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/actions/cancel")
    public Result<ServiceOrderVO> cancel(@Valid @RequestBody ServiceOrderActionDTO command) {
        return Result.success(service.cancel(command));
    }

    @Operation(summary = "标记服务预约已完成")
    @PreAuthorize("hasAnyRole('ELDER_OPERATOR', 'ORG_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/actions/complete")
    public Result<ServiceOrderVO> complete(@Valid @RequestBody ServiceOrderActionDTO command) {
        return Result.success(service.complete(command));
    }
}
