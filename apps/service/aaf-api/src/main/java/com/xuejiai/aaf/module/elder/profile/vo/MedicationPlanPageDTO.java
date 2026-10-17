package com.xuejiai.aaf.module.elder.profile.vo;

import com.xuejiai.aaf.common.enums.elder.MedicationPlanSourceEnum;
import com.xuejiai.aaf.common.enums.elder.MedicationPlanStatusEnum;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用药计划分页查询请求。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用药计划分页查询")
public class MedicationPlanPageDTO extends PageParam {

    private Long elderId;
    private String medicationName;

    @InEnum(value = MedicationPlanStatusEnum.class, message = "用药计划状态必须是 {value}")
    private String status;

    @InEnum(value = MedicationPlanSourceEnum.class, message = "用药计划来源必须是 {value}")
    private String source;
}
