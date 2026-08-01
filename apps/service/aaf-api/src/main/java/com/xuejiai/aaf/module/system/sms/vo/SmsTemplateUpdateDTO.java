package com.xuejiai.aaf.module.system.sms.vo;

/**
 * 短信模板更新请求。
 *
 * <p>M22：原为 SmsController 内部类，随模板 CRUD 迁移到 service 层一并挪出，避免 service 反向依赖 controller。
 *
 * @author AaronZZH & Kiro
 */
public record SmsTemplateUpdateDTO(
        String signName, String apiTemplateId, String provider, Short status) {}
