package com.xuejiai.aaf.common.enums.sys;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 短信模板类型枚举，对应字典 sys_sms_template_type。 */
@Getter
@AllArgsConstructor
public enum SmsTemplateTypeEnum {
    VERIFY_CODE(1, "验证码"),
    NOTIFY(2, "通知"),
    MARKETING(3, "营销");

    private final Integer code;
    private final String label;
}
