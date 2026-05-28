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
}
