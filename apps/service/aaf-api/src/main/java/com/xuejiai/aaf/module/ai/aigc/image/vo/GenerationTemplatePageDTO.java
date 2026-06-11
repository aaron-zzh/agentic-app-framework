package com.xuejiai.aaf.module.ai.aigc.image.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 参数模板分页查询参数。 */
@Schema(description = "参数模板分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class GenerationTemplatePageDTO extends PageParam {

    @Schema(description = "模板类型：IMAGE / VIDEO / COPYWRITING")
    private String type;

    @Schema(description = "使用场景：GENERATION / PROJECT")
    private String scope;

    @Schema(description = "模板分类")
    private String category;

    @Schema(description = "是否只查公开模板")
    private Boolean isPublic;
}
