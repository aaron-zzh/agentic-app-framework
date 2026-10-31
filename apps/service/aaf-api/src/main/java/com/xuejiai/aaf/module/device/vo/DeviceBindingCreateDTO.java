package com.xuejiai.aaf.module.device.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 当前登录用户使用设备屏幕配对码绑定自己的设备。 */
@Schema(description = "用户自助绑定设备")
public record DeviceBindingCreateDTO(
        @NotBlank @Size(min = 8, max = 8) @Schema(description = "设备屏幕上的配对码") String pairCode) {}
