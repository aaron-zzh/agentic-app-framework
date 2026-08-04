package com.xuejiai.aaf.module.ai.aigc.timeline.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AigcTimelinePageDTO extends PageParam {
    private Long projectId;
    private Long deliverableObjectId;
    private String status;
}
