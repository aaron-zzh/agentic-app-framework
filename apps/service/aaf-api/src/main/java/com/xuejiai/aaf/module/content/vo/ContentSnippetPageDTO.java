package com.xuejiai.aaf.module.content.vo;

import com.xuejiai.aaf.common.enums.content.ContentProjectTypeEnum;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 创作片段分页查询。
 *
 * @author AaronZZH & Kiro
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "创作片段分页查询")
public class ContentSnippetPageDTO extends PageParam {

    @Schema(description = "category 筛选")
    private String category;

    @InEnum(value = ContentProjectTypeEnum.class, message = "projectTypeCode 必须是 {value}")
    @Schema(description = "projectTypeCode 筛选")
    private String projectTypeCode;
}
