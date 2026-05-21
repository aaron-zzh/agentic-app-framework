package com.xuejiai.aaf.module.system.workflow.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 委托分页查询请求。 */
@Schema(description = "委托分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class DelegationPageDTO extends PageParam {

    @Schema(description = "状态筛选：active / expired / cancelled")
    private String status;
}
