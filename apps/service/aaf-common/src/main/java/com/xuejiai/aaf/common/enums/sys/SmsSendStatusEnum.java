package com.xuejiai.aaf.common.enums.sys;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 短信发送状态枚举，对应字典 sys_sms_send_status。 */
@Getter
@AllArgsConstructor
public enum SmsSendStatusEnum implements ArrayValuable<Integer> {
    INIT(0, "初始化"),
    SUCCESS(10, "发送成功"),
    FAILURE(20, "发送失败"),
    IGNORE(30, "不发送");

    private final Integer code;
    private final String label;

    public static final Integer[] ARRAYS =
            Arrays.stream(values()).map(SmsSendStatusEnum::getCode).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
