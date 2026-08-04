package com.xuejiai.aaf.module.ai.aigc.media.vo;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaType;

import lombok.Getter;
import lombok.Setter;

/** AIGC 资产分页查询参数。 */
@Getter
@Setter
public class AigcAssetPageDTO extends PageParam {
    private AigcMediaType mediaType;
    private Long categoryId;
    private String keyword;
}
