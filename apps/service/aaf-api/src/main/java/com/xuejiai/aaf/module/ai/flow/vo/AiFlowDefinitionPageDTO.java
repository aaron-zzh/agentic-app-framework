package com.xuejiai.aaf.module.ai.flow.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** AI 工作流定义分页查询请求 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiFlowDefinitionPageDTO extends PageParam {
    private String name;
    private String status;
    private Boolean agentCallable;
}
