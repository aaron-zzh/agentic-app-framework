package com.xuejiai.aaf.module.ai.aigc.template.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 项目模板分页查询参数。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserProjectTemplatePageDTO extends PageParam {
    private String category;
    private Boolean isOfficial;
    private String keyword;
}
