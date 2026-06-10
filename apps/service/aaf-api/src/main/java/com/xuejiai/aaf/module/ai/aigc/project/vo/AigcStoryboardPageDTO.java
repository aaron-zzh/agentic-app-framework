package com.xuejiai.aaf.module.ai.aigc.project.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 分镜规划分页查询 DTO。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AigcStoryboardPageDTO extends PageParam {
    private Long projectId;
    private String status;
}
