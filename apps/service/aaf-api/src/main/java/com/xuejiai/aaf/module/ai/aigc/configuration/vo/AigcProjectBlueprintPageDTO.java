package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;
import com.xuejiai.aaf.module.ai.aigc.configuration.enums.AigcConfigStatus;
import com.xuejiai.aaf.module.ai.aigc.configuration.enums.AigcProductionMode;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目蓝图分页查询。
 *
 * @author AaronZZH & Kiro
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "项目蓝图分页查询")
public class AigcProjectBlueprintPageDTO extends PageParam {

    @Schema(description = "projectTypeCode 筛选")
    private String projectTypeCode;

    @InEnum(value = AigcProductionMode.class, message = "productionMode 必须是 {value}")
    @Schema(description = "productionMode 筛选")
    private String productionMode;

    @InEnum(value = AigcConfigStatus.class, message = "status 必须是 {value}")
    @Schema(description = "status 筛选")
    private String status;
}
