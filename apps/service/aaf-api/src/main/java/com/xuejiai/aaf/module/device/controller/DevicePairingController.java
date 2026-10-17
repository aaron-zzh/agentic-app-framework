package com.xuejiai.aaf.module.device.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.protection.RateLimit;
import com.xuejiai.aaf.module.device.service.DevicePairingService;
import com.xuejiai.aaf.module.device.vo.DevicePairingVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** 无需登录的设备屏幕配对接口，由独立设备安全链显式放行。 */
@Tag(name = "设备-配对")
@RestController
@RequestMapping("/api/device/pair")
public class DevicePairingController {

    private final DevicePairingService pairingService;

    public DevicePairingController(DevicePairingService pairingService) {
        this.pairingService = pairingService;
    }

    @Operation(summary = "开始设备配对")
    @RateLimit(
            limit = 10,
            windowSeconds = 60,
            prefix = "device-pair-start-source",
            message = "设备配对请求过于频繁，请稍后再试")
    @PostMapping("/start")
    public Result<DevicePairingVO.StartResponse> start(
            @Valid @RequestBody DevicePairingVO.StartRequest request) {
        return Result.success(
                pairingService.start(
                        request.serialNumber(), request.model(), request.firmwareVersion()));
    }

    @Operation(summary = "轮询设备配对结果")
    @RateLimit(
            limit = 120,
            windowSeconds = 60,
            prefix = "device-pair-poll-source",
            message = "设备配对轮询过于频繁，请稍后再试")
    @PostMapping("/poll")
    public Result<DevicePairingVO.PollResponse> poll(
            @Valid @RequestBody DevicePairingVO.PollRequest request) {
        var apiBaseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        return Result.success(pairingService.poll(request.deviceCode(), apiBaseUrl));
    }
}
