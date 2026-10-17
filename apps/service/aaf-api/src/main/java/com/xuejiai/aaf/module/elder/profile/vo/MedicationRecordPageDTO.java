package com.xuejiai.aaf.module.elder.profile.vo;

import com.xuejiai.aaf.common.enums.elder.MedicationRecordResultEnum;
import com.xuejiai.aaf.common.enums.elder.MedicationRecordSourceEnum;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 服药记录分页查询请求。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "服药记录分页查询")
public class MedicationRecordPageDTO extends PageParam {

    private Long elderId;
    private Long planId;

    @InEnum(value = MedicationRecordResultEnum.class, message = "服药结果必须是 {value}")
    private String result;

    @InEnum(value = MedicationRecordSourceEnum.class, message = "记录来源必须是 {value}")
    private String source;
}
