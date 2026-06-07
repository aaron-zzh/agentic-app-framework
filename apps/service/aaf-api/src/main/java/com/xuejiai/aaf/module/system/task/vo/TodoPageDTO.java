package com.xuejiai.aaf.module.system.task.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 待办分页查询请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "待办分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class TodoPageDTO extends PageParam {

    @Schema(description = "状态筛选：pending / done / ignored")
    private String status;

    @Schema(description = "分类筛选：todo / call / email / meeting")
    private String category;

    @Schema(description = "来源实体类型")
    private String sourceEntity;

    @Schema(description = "来源实体 ID")
    private Long sourceId;
}
