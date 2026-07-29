package com.xuejiai.aaf.module.content.vo;

import com.xuejiai.aaf.common.enums.content.ContentConfigStatusEnum;
import com.xuejiai.aaf.common.enums.content.ContentProductionModeEnum;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;

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
public class ContentProjectBlueprintPageDTO extends PageParam {

    @Schema(description = "projectTypeCode 筛选")
    private String projectTypeCode;

    @InEnum(value = ContentProductionModeEnum.class, message = "productionMode 必须是 {value}")
    @Schema(description = "productionMode 筛选")
    private String productionMode;

    @InEnum(value = ContentConfigStatusEnum.class, message = "status 必须是 {value}")
    @Schema(description = "status 筛选")
    private String status;
}
