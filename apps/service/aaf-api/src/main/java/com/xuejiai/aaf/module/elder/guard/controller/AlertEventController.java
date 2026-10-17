package com.xuejiai.aaf.module.elder.guard.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.elder.guard.domain.AlertEvent;
import com.xuejiai.aaf.module.elder.guard.service.AlertEventService;
import com.xuejiai.aaf.module.elder.guard.vo.AlertActionDTO;
import com.xuejiai.aaf.module.elder.guard.vo.AlertEventCreateDTO;
import com.xuejiai.aaf.module.elder.guard.vo.AlertEventPageDTO;
import com.xuejiai.aaf.module.elder.guard.vo.AlertEventUpdateDTO;
import com.xuejiai.aaf.module.elder.guard.vo.AlertEventVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 养老预警管理接口；标准创建与删除能力已在资源定义中关闭。 */
@Tag(name = "养老-预警管理")
@RestController
@RequestMapping("/api/elder/guard/alerts")
@RequiredArgsConstructor
public class AlertEventController
        extends BaseCrudController<
                AlertEvent,
                AlertEventVO,
                AlertEventCreateDTO,
                AlertEventUpdateDTO,
                AlertEventPageDTO> {

    private final AlertEventService service;

    @Override
    protected AlertEventService getService() {
        return service;
    }

    @Operation(summary = "认领预警")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/actions/claim")
    public Result<AlertEventVO> claim(@Valid @RequestBody AlertActionDTO command) {
        return Result.success(service.claim(command.id()));
    }

    @Operation(summary = "代记联系人已知晓")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/actions/acknowledge")
    public Result<AlertEventVO> acknowledge(@Valid @RequestBody AlertActionDTO command) {
        return Result.success(service.acknowledge(command.id(), command.acknowledgedContactId()));
    }
}
