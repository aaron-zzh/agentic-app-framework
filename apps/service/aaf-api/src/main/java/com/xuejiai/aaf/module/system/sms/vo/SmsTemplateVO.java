package com.xuejiai.aaf.module.system.sms.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 短信模板响应 VO。
 *
 * <p>M22：原 SmsController 直接返回 SmsTemplate 实体，改为 VO 出参，与 MessageTemplateVO 同一模式。
 *
 * @author AaronZZH & Kiro
 */
public record SmsTemplateVO(
        @Schema(description = "主键 ID") Long id,
        @Schema(description = "业务场景编码") String code,
        @Schema(description = "模板名称") String name,
        @Schema(description = "签名") String signName,
        @Schema(description = "厂商模板 ID") String apiTemplateId,
        @Schema(description = "参数名列表（JSON 数组）") String params,
        @Schema(description = "指定厂商") String provider,
        @Schema(description = "状态：1=启用 0=禁用") Short status,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
