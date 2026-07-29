package com.xuejiai.aaf.module.content.vo;

import com.xuejiai.aaf.common.enums.content.ContentObjectStatusEnum;
import com.xuejiai.aaf.common.enums.content.ContentObjectTypeEnum;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目对象分页查询。
 *
 * @author AaronZZH & Kiro
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "项目对象分页查询")
public class ContentProjectObjectPageDTO extends PageParam {

    @Schema(description = "projectId 筛选")
    private Long projectId;

    @InEnum(value = ContentObjectTypeEnum.class, message = "objectType 必须是 {value}")
    @Schema(description = "objectType 筛选")
    private String objectType;

    @InEnum(value = ContentObjectStatusEnum.class, message = "status 必须是 {value}")
    @Schema(description = "status 筛选")
    private String status;
}
