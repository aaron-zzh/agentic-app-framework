package com.xuejiai.aaf.module.system.log.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审计日志分页查询请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "审计日志分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class AuditLogPageDTO extends PageParam {

    @Schema(description = "实体类型")
    private String entityType;

    @Schema(description = "实体 ID")
    private Long entityId;

    @Schema(description = "操作类型")
    private String action;

    @Schema(description = "操作用户 ID")
    private Long userId;
}
