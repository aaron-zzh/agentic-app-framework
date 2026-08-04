package com.xuejiai.aaf.module.ai.aigc.media.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AigcAssetCollectionPageDTO extends PageParam {
    private String name;
    private String collectionType;
}
