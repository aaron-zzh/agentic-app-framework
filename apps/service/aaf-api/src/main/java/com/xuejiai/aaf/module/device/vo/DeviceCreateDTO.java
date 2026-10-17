package com.xuejiai.aaf.module.device.vo;

import com.xuejiai.aaf.common.enums.device.DeviceTypeEnum;
import com.xuejiai.aaf.common.enums.device.DeviceZoneEnum;
import com.xuejiai.aaf.common.validation.InEnum;
import com.xuejiai.aaf.framework.crud.reference.ResourceReference;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 创建设备并完成配对绑定。硬件信息以配对会话为准，不接受管理端覆盖。 */
@Schema(description = "创建设备并绑定")
public record DeviceCreateDTO(
        @NotBlank @Size(min = 8, max = 8) @Schema(description = "设备屏幕上的配对码") String pairCode,
        @Size(max = 64) @Schema(description = "设备场景；为空时取首个已启用场景") String scenarioKey,
        @Valid @Schema(description = "场景主体；无主体的场景可为空") ResourceReference subject,
        @Size(max = 128) @Schema(description = "设备级角色覆盖") String roleKey,
        @NotNull @Positive @Schema(description = "设备所有者用户 ID，也是计费主体") Long ownerUserId,
        @NotBlank @Size(max = 100) @Schema(description = "设备名称") String name,
        @InEnum(value = DeviceTypeEnum.class, message = "设备类型必须是 {value}") String deviceType,
        @InEnum(value = DeviceZoneEnum.class, message = "设备区域必须是 {value}") String zone) {}
