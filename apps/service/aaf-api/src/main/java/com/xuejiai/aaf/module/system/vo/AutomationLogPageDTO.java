package com.xuejiai.aaf.module.system.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 自动化日志分页查询请求。 */
@Schema(description = "自动化日志分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class AutomationLogPageDTO extends PageParam {

    @Schema(description = "规则 ID")
    private Long ruleId;

    @Schema(description = "执行状态：success/failed/skipped")
    private String status;
}
