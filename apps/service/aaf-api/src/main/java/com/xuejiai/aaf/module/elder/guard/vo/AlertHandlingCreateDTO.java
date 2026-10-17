package com.xuejiai.aaf.module.elder.guard.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** 预警处理创建类型契约；管理端已关闭创建能力，记录由预警流程内部追加。 */
@Schema(description = "预警处理创建类型契约")
public record AlertHandlingCreateDTO(
        Long alertId,
        String handlingType,
        String channel,
        String status,
        String recipientType,
        Long recipientId,
        Long operatorUserId,
        Long acknowledgedContactId,
        String detail,
        String errorMessage,
        LocalDateTime handledAt) {}
