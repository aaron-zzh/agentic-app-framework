package com.xuejiai.aaf.module.device.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.device.security.DevicePrincipal;
import com.xuejiai.aaf.module.device.service.DeviceBriefService;
import com.xuejiai.aaf.module.device.vo.DeviceBriefVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 设备凭据专用的机器人简报接口。 */
@Tag(name = "设备简报")
@RestController
@RequestMapping("/api/device/brief")
public class DeviceBriefController {

    private final DeviceBriefService briefService;

    public DeviceBriefController(DeviceBriefService briefService) {
        this.briefService = briefService;
    }

    @Operation(summary = "获取当前设备启动简报")
    @PreAuthorize("hasRole('DEVICE')")
    @GetMapping
    public Result<DeviceBriefVO> getBrief(@AuthenticationPrincipal DevicePrincipal principal) {
        return Result.success(briefService.getBrief(principal));
    }
}
