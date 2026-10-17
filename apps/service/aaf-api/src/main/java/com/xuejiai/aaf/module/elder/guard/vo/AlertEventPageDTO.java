package com.xuejiai.aaf.module.elder.guard.vo;

import com.xuejiai.aaf.common.enums.elder.AlertLevelEnum;
import com.xuejiai.aaf.common.enums.elder.AlertStatusEnum;
import com.xuejiai.aaf.common.enums.elder.AlertTypeEnum;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 预警分页查询请求。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "预警分页查询")
public class AlertEventPageDTO extends PageParam {

    private Long deviceId;
    private Long elderId;

    @InEnum(value = AlertTypeEnum.class, message = "预警类型必须是 {value}")
    private String alertType;

    @InEnum(value = AlertLevelEnum.class, message = "预警级别必须是 {value}")
    private String level;

    @InEnum(value = AlertStatusEnum.class, message = "预警状态必须是 {value}")
    private String status;

    private Boolean late;
    private Long claimedBy;
}
