package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Getter;
import lombok.Setter;

/** 创作片段分页查询。 */
@Getter
@Setter
public class AigcSnippetPageDTO extends PageParam {
    private String category;
    private String projectTypeCode;
}
