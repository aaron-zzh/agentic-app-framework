package com.xuejiai.aaf.framework.messaging.sms;

import java.util.Optional;

/**
 * 短信模板提供者接口，由业务层实现（从 sys_sms_template 加载）。
 *
 * <p>与 {@link com.xuejiai.aaf.framework.messaging.MessageTemplateProvider}
 * 互补：MessageTemplateProvider 提供邮件等需要本地内容渲染的模板， SmsTemplateProvider 提供短信厂商模板（厂商侧已审核
 * api_template_id，本地不渲染内容）。
 */
public interface SmsTemplateProvider {

    /** 按业务编码查找启用状态的模板 */
    Optional<SmsTemplateInfo> findByCode(String code);

    /**
     * 短信模板信息。
     *
     * @param code 业务场景编码（如 register/login/reset）
     * @param apiTemplateId 厂商模板 ID（如阿里云 SMS_xxx）
     * @param provider 指定厂商（null=系统默认）
     */
    record SmsTemplateInfo(String code, String apiTemplateId, String provider) {}
}
