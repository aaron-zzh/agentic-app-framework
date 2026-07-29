package com.xuejiai.aaf.module.content.vo;

import com.xuejiai.aaf.common.enums.content.ContentProductionModeEnum;
import com.xuejiai.aaf.common.enums.content.ContentProjectStatusEnum;
import com.xuejiai.aaf.common.enums.content.ContentProjectTypeEnum;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 内容项目分页查询。
 *
 * @author AaronZZH & Kiro
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "内容项目分页查询")
public class ContentProjectPageDTO extends PageParam {

    @InEnum(value = ContentProjectStatusEnum.class, message = "status 必须是 {value}")
    @Schema(description = "status 筛选")
    private String status;

    @InEnum(value = ContentProjectTypeEnum.class, message = "projectTypeCode 必须是 {value}")
    @Schema(description = "projectTypeCode 筛选")
    private String projectTypeCode;

    @InEnum(value = ContentProductionModeEnum.class, message = "productionMode 必须是 {value}")
    @Schema(description = "productionMode 筛选")
    private String productionMode;
}
