package com.xuejiai.aaf.module.elder.guard.vo;

import com.xuejiai.aaf.common.enums.elder.AlertHandlingChannelEnum;
import com.xuejiai.aaf.common.enums.elder.AlertHandlingStatusEnum;
import com.xuejiai.aaf.common.enums.elder.AlertHandlingTypeEnum;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 预警处理记录分页查询请求。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "预警处理记录分页查询")
public class AlertHandlingPageDTO extends PageParam {

    private Long alertId;

    @InEnum(value = AlertHandlingTypeEnum.class, message = "处理类型必须是 {value}")
    private String handlingType;

    @InEnum(value = AlertHandlingChannelEnum.class, message = "处理渠道必须是 {value}")
    private String channel;

    @InEnum(value = AlertHandlingStatusEnum.class, message = "处理状态必须是 {value}")
    private String status;

    private Long operatorUserId;
    private Long acknowledgedContactId;
}
