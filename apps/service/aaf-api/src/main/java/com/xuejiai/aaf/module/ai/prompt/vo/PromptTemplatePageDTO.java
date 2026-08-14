package com.xuejiai.aaf.module.ai.prompt.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 提示词资产分页参数。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PromptTemplatePageDTO extends PageParam {

    private String type;
    private String scope;
    private String category;
    private Boolean isPublic;

    @Schema(hidden = true)
    private Boolean ownerOnly;
}
