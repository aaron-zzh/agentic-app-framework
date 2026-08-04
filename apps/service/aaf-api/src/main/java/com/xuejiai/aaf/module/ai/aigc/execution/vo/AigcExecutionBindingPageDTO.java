package com.xuejiai.aaf.module.ai.aigc.execution.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AigcExecutionBindingPageDTO extends PageParam {

    private String actionKey;
    private String targetType;
    private String status;
}
