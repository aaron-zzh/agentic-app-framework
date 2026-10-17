package com.xuejiai.aaf.module.device.vo;

import com.xuejiai.aaf.common.enums.device.DeviceOnlineStatusEnum;
import com.xuejiai.aaf.common.enums.device.DeviceStatusEnum;
import com.xuejiai.aaf.common.enums.device.DeviceTypeEnum;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 设备分页查询请求。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "设备分页查询")
public class DevicePageDTO extends PageParam {

    @InEnum(value = DeviceStatusEnum.class, message = "设备状态必须是 {value}")
    private String status;

    @InEnum(value = DeviceOnlineStatusEnum.class, message = "在线状态必须是 {value}")
    private String onlineStatus;

    @InEnum(value = DeviceTypeEnum.class, message = "设备类型必须是 {value}")
    private String deviceType;

    private String scenarioKey;
    private Long ownerUserId;
}
