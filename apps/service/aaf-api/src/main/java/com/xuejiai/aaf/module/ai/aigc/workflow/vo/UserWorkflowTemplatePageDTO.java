package com.xuejiai.aaf.module.ai.aigc.workflow.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户工作流模板分页查询参数。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserWorkflowTemplatePageDTO extends PageParam {
    private String category;
    private Boolean isOfficial;
    private String keyword;
}
