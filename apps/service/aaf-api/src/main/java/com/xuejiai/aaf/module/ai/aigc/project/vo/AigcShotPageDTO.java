package com.xuejiai.aaf.module.ai.aigc.project.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 分镜分页查询 DTO。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AigcShotPageDTO extends PageParam {
    private Long storyboardId;
}
