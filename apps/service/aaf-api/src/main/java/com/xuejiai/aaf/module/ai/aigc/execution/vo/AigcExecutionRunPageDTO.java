package com.xuejiai.aaf.module.ai.aigc.execution.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AigcExecutionRunPageDTO extends PageParam {

    private Long projectId;
    private Long objectId;
    private String status;
}
