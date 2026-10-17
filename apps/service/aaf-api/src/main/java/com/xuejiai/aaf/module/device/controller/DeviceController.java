package com.xuejiai.aaf.module.device.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.device.domain.Device;
import com.xuejiai.aaf.module.device.service.DeviceService;
import com.xuejiai.aaf.module.device.vo.DeviceActionDTO;
import com.xuejiai.aaf.module.device.vo.DeviceCreateDTO;
import com.xuejiai.aaf.module.device.vo.DevicePageDTO;
import com.xuejiai.aaf.module.device.vo.DeviceUpdateDTO;
import com.xuejiai.aaf.module.device.vo.DeviceVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** 设备管理接口。管理路径与设备凭据路径隔离。 */
@Tag(name = "设备管理")
@RestController
@RequestMapping("/api/device/devices")
public class DeviceController
        extends BaseCrudController<
                Device, DeviceVO, DeviceCreateDTO, DeviceUpdateDTO, DevicePageDTO> {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Override
    protected BaseCrudService<Device, DeviceVO, DeviceCreateDTO, DeviceUpdateDTO, DevicePageDTO>
            getService() {
        return deviceService;
    }

    @Operation(summary = "解绑设备", description = "立即撤销设备凭据并断开 MQTT 连接")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/actions/unbind")
    public Result<DeviceVO> unbind(@Valid @RequestBody DeviceActionDTO request) {
        return Result.success(deviceService.unbind(request.id()));
    }
}
