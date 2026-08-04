package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Getter;
import lombok.Setter;

/** 项目类型兼容包分页条件。 */
@Getter
@Setter
public class AigcProjectTypePackagePageDTO extends PageParam {
    private Long projectTypeId;
    private String productionMode;
    private String status;
}
