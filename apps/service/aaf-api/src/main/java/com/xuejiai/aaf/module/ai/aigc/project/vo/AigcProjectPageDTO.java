package com.xuejiai.aaf.module.ai.aigc.project.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AigcProjectPageDTO extends PageParam {

    private String status;
    private String projectTypeCode;
    private String productionMode;
}
