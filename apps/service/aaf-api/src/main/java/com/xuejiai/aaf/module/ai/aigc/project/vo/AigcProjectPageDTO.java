package com.xuejiai.aaf.module.ai.aigc.project.vo;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectLifecycle;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AigcProjectPageDTO extends PageParam {

    private AigcProjectLifecycle status;
    private String projectTypeCode;
    private String productionMode;
}
