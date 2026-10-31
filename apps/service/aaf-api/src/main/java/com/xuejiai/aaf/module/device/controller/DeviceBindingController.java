package com.xuejiai.aaf.module.device.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.device.service.DeviceService;
import com.xuejiai.aaf.module.device.vo.DeviceBindingCreateDTO;
import com.xuejiai.aaf.module.device.vo.DeviceBindingVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** 用户自助绑定入口。设备所有者始终取当前登录用户，不接受客户端指定。 */
@Tag(name = "设备-用户绑定")
@RestController
@RequestMapping("/api/device-bindings")
public class DeviceBindingController {

    private final DeviceService deviceService;

    public DeviceBindingController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Operation(summary = "绑定设备到当前用户")
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public Result<DeviceBindingVO> bind(@Valid @RequestBody DeviceBindingCreateDTO request) {
        return Result.success(deviceService.bindCurrentUser(request));
    }
}
