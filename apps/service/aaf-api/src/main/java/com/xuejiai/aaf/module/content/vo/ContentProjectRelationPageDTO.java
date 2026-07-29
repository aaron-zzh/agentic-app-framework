package com.xuejiai.aaf.module.content.vo;

import com.xuejiai.aaf.common.enums.content.ContentRelationLayerEnum;
import com.xuejiai.aaf.common.enums.content.ContentRelationTypeEnum;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目关系分页查询。
 *
 * @author AaronZZH & Kiro
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "项目关系分页查询")
public class ContentProjectRelationPageDTO extends PageParam {

    @Schema(description = "projectId 筛选")
    private Long projectId;

    @InEnum(value = ContentRelationTypeEnum.class, message = "relationType 必须是 {value}")
    @Schema(description = "relationType 筛选")
    private String relationType;

    @InEnum(value = ContentRelationLayerEnum.class, message = "layer 必须是 {value}")
    @Schema(description = "layer 筛选")
    private String layer;
}
