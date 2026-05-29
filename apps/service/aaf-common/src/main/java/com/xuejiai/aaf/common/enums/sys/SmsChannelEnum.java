package com.xuejiai.aaf.common.enums.sys;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 短信渠道枚举，对应字典 sys_sms_channel，值与 SmsProperties.provider 一致。 */
@Getter
@AllArgsConstructor
public enum SmsChannelEnum implements ArrayValuable<String> {
    ALIYUN("aliyun", "阿里云"),
    TENCENT("tencent", "腾讯云");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(SmsChannelEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
