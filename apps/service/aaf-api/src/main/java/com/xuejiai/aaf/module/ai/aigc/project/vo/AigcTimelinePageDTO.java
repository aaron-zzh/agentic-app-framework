package com.xuejiai.aaf.module.ai.aigc.project.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 时间轴分页查询 DTO。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AigcTimelinePageDTO extends PageParam {
    private Long projectId;
    private Long storyboardId;
    private String status;
}
