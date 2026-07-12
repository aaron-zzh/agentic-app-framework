package com.xuejiai.aaf.module.system.task.vo;

import com.xuejiai.aaf.common.enums.sys.TodoCategoryEnum;
import com.xuejiai.aaf.common.enums.sys.TodoStatusEnum;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;

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

    @InEnum(value = TodoStatusEnum.class, message = "状态必须是 {value}")
    @Schema(description = "状态筛选：pending / done / ignored")
    private String status;

    @InEnum(value = TodoCategoryEnum.class, message = "分类必须是 {value}")
    @Schema(description = "分类筛选：todo / call / email / meeting")
    private String category;

    @Schema(description = "来源实体类型")
    private String sourceEntity;

    @Schema(description = "来源实体 ID")
    private Long sourceId;
}
