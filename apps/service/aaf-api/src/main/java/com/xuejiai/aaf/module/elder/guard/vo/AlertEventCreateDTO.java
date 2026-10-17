package com.xuejiai.aaf.module.elder.guard.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** 预警创建类型契约；管理端已关闭创建能力，实例由设备事件或后端判定生成。 */
@Schema(description = "预警创建类型契约")
public record AlertEventCreateDTO(
        Long deviceId,
        Long elderId,
        String eventId,
        String alertType,
        String level,
        String zone,
        BigDecimal confidence,
        String payload,
        LocalDateTime deviceOccurredAt,
        Boolean late) {}
