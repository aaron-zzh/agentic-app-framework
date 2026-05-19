package com.xuejiai.aaf.module.system.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** 委托响应。 */
@Schema(description = "审批委托信息")
public record DelegationVO(
        Long id,
        Long delegatorId,
        Long delegateId,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String processKeys,
        String status,
        LocalDateTime createTime) {}
