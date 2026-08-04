package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;
import com.xuejiai.aaf.module.ai.aigc.configuration.enums.AigcConfigStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 渠道规格分页查询。
 *
 * @author AaronZZH & Kiro
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "渠道规格分页查询")
public class AigcChannelSpecPageDTO extends PageParam {

    @InEnum(value = AigcConfigStatus.class, message = "status 必须是 {value}")
    @Schema(description = "status 筛选")
    private String status;
}
