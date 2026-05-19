package com.xuejiai.aaf.module.system.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 回收站分页查询请求。 */
@Schema(description = "回收站分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class TrashPageDTO extends PageParam {

    @Schema(description = "实体类型筛选，如 user / todo / document")
    private String entityType;
}
