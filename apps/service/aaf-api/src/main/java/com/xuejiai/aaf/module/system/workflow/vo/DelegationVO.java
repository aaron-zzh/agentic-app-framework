package com.xuejiai.aaf.module.system.workflow.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 委托响应。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "审批委托信息")
public record DelegationVO(
        @Schema(description = "委托 ID") Long id,
        @Schema(description = "委托人 ID") Long delegatorId,
        @Schema(description = "代理人 ID") Long delegateId,
        @Schema(description = "开始时间") LocalDateTime startDate,
        @Schema(description = "结束时间") LocalDateTime endDate,
        @Schema(description = "适用流程 key 列表") String processKeys,
        @Schema(description = "状态") String status,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
