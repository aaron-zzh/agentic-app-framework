package com.xuejiai.aaf.module.ai.aigc.media.vo;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.module.ai.aigc.media.enums.MediaSourceType;
import com.xuejiai.aaf.module.ai.aigc.media.enums.MediaType;

import lombok.Getter;
import lombok.Setter;

/** Media 分页查询参数。 */
@Getter
@Setter
public class MediaPageDTO extends PageParam {
    private MediaType mediaType;
    private MediaSourceType sourceType;
    private Long projectId;
    private String keyword;
}
