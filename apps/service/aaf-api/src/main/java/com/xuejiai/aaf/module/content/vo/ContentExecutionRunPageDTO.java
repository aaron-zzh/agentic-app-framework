package com.xuejiai.aaf.module.content.vo;

import com.xuejiai.aaf.common.enums.content.ContentExecutionStatusEnum;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 执行记录分页查询。
 *
 * @author AaronZZH & Kiro
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "执行记录分页查询")
public class ContentExecutionRunPageDTO extends PageParam {

    @Schema(description = "projectId 筛选")
    private Long projectId;

    @Schema(description = "objectId 筛选")
    private Long objectId;

    @InEnum(value = ContentExecutionStatusEnum.class, message = "status 必须是 {value}")
    @Schema(description = "status 筛选")
    private String status;
}
