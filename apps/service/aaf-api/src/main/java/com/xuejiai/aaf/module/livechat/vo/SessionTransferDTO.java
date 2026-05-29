package com.xuejiai.aaf.module.livechat.vo;

import com.xuejiai.aaf.common.enums.livechat.TransferReasonEnum;

import jakarta.validation.constraints.NotNull;

/** 会话转接请求 DTO。 */
public record SessionTransferDTO(
        @NotNull Long toStaffId,
        String toSkillGroup,
        @NotNull TransferReasonEnum reason,
        String note) {}
