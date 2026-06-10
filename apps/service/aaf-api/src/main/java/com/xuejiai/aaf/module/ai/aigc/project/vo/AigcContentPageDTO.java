package com.xuejiai.aaf.module.ai.aigc.project.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 内容产出分页查询 DTO。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AigcContentPageDTO extends PageParam {
    private Long projectId;
    private String type;
    private String publishStatus;
}
