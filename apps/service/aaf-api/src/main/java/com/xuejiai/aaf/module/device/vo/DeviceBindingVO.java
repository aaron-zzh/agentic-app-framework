package com.xuejiai.aaf.module.device.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 自助绑定成功后返回给当前用户的最小设备摘要。 */
@Schema(description = "设备绑定结果")
public record DeviceBindingVO(
        Long id, String name, String serialNumber, String model, String status) {}
