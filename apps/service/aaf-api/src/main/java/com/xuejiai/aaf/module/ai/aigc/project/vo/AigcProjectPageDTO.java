package com.xuejiai.aaf.module.ai.aigc.project.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 创作项目分页查询 DTO。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AigcProjectPageDTO extends PageParam {
    private String name;
    private String status;
    private String type;
}
