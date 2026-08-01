package com.xuejiai.aaf.module.system.sms.vo;

import jakarta.validation.constraints.NotBlank;

/**
 * 短信模板创建请求。
 *
 * <p>M22：原为 SmsController 内部类，随模板 CRUD 迁移到 service 层一并挪出，避免 service 反向依赖 controller。
 *
 * @author AaronZZH & Kiro
 */
public record SmsTemplateCreateDTO(
        @NotBlank String code,
        @NotBlank String name,
        String signName,
        @NotBlank String apiTemplateId,
        String params,
        String provider) {}
