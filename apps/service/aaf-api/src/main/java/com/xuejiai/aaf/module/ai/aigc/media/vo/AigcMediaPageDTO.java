package com.xuejiai.aaf.module.ai.aigc.media.vo;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaType;
import com.xuejiai.aaf.module.ai.aigc.media.enums.AigcMediaSourceType;

import lombok.Getter;
import lombok.Setter;

/** AIGC 媒体分页查询参数。 */
@Getter
@Setter
public class AigcMediaPageDTO extends PageParam {
    private AigcMediaType mediaType;
    private AigcMediaSourceType sourceType;
    private Long projectId;
    private String keyword;
}
