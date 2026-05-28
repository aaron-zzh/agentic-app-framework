package com.xuejiai.aaf.module.system.workflow.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 创建委托请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建审批委托")
public record DelegationCreateDTO(
        @NotNull @Schema(description = "代理人 ID") Long delegateId,
        @NotNull @Schema(description = "开始时间") LocalDateTime startDate,
        @NotNull @Schema(description = "结束时间") LocalDateTime endDate,
        @Schema(description = "适用流程 key 列表（JSON），null 表示全部") String processKeys) {}
