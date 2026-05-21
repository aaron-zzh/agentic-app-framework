package com.xuejiai.aaf.common.enums.sys;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 短信渠道枚举，对应字典 sys_sms_channel，值与 SmsProperties.provider 一致。 */
@Getter
@AllArgsConstructor
public enum SmsChannelEnum {
    ALIYUN("aliyun", "阿里云"),
    TENCENT("tencent", "腾讯云");

    private final String code;
    private final String label;
}
