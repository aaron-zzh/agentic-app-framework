package com.xuejiai.aaf.module.elder.profile.vo;

import com.xuejiai.aaf.common.enums.elder.HealthItemTypeEnum;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 长者健康条目分页查询请求。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "长者健康条目分页查询")
public class ElderHealthItemPageDTO extends PageParam {

    private Long elderId;

    @InEnum(value = HealthItemTypeEnum.class, message = "健康条目类型必须是 {value}")
    private String itemType;

    private String name;
}
