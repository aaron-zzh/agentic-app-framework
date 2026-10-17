package com.xuejiai.aaf.module.elder.mall.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.elder.mall.ServiceItemResource;
import com.xuejiai.aaf.module.elder.mall.domain.ServiceItem;
import com.xuejiai.aaf.module.elder.mall.service.ServiceItemService;
import com.xuejiai.aaf.module.elder.mall.vo.ServiceItemActionDTO;
import com.xuejiai.aaf.module.elder.mall.vo.ServiceItemCreateDTO;
import com.xuejiai.aaf.module.elder.mall.vo.ServiceItemPageDTO;
import com.xuejiai.aaf.module.elder.mall.vo.ServiceItemUpdateDTO;
import com.xuejiai.aaf.module.elder.mall.vo.ServiceItemVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 服务项目管理接口。 */
@Tag(name = "养老-服务项目")
@RestController
@RequestMapping(ServiceItemResource.BASE_PATH)
@RequiredArgsConstructor
public class ServiceItemController
        extends BaseCrudController<
                ServiceItem,
                ServiceItemVO,
                ServiceItemCreateDTO,
                ServiceItemUpdateDTO,
                ServiceItemPageDTO> {

    private final ServiceItemService service;

    @Override
    protected ServiceItemService getService() {
        return service;
    }

    @Operation(summary = "上架服务项目")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/actions/publish")
    public Result<ServiceItemVO> publish(@Valid @RequestBody ServiceItemActionDTO command) {
        return Result.success(service.publish(command));
    }

    @Operation(summary = "下架服务项目")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/actions/offline")
    public Result<ServiceItemVO> offline(@Valid @RequestBody ServiceItemActionDTO command) {
        return Result.success(service.offline(command));
    }
}
