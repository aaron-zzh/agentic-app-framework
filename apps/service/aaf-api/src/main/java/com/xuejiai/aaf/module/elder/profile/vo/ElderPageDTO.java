package com.xuejiai.aaf.module.elder.profile.vo;

import com.xuejiai.aaf.common.enums.elder.ElderLivingArrangementEnum;
import com.xuejiai.aaf.common.enums.elder.ElderStatusEnum;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 长者档案分页查询请求。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "长者档案分页查询")
public class ElderPageDTO extends PageParam {

    @InEnum(value = ElderStatusEnum.class, message = "档案状态必须是 {value}")
    private String status;

    @InEnum(value = ElderLivingArrangementEnum.class, message = "居住形态必须是 {value}")
    private String livingArrangement;

    private Long userId;
}
