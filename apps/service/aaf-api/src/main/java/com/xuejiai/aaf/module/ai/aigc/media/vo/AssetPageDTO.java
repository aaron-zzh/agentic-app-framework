package com.xuejiai.aaf.module.ai.aigc.media.vo;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.module.ai.aigc.media.enums.MediaType;

import lombok.Getter;
import lombok.Setter;

/** Asset 分页查询参数。 */
@Getter
@Setter
public class AssetPageDTO extends PageParam {
    private MediaType mediaType;
    private Long categoryId;
    private String keyword;
}
