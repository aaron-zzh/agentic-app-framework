package com.xuejiai.aaf.module.ai.aigc.video.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 视频模板分页查询 DTO。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class VideoTemplatePageDTO extends PageParam {

    @Schema(description = "模板类型：INTRO/OUTRO/TRANSITION/SUBTITLE")
    private String type;
}
