package com.xuejiai.aaf.module.ai.aigc.work.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AigcWorkPageDTO extends PageParam {
    private Long projectId;
    private String status;
    private String visibility;
}
