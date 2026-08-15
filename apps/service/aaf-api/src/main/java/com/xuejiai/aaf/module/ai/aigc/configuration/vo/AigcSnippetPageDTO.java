package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/** 创作片段分页查询。 */
@Getter
@Setter
public class AigcSnippetPageDTO extends PageParam {
    private String category;
    private String projectTypeCode;

    @Schema(hidden = true)
    private Boolean ownerOnly;

    @Schema(hidden = true)
    private Boolean publicOnly;
}
