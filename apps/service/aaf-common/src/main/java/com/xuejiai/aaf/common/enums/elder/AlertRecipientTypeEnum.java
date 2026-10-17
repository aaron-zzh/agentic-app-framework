package com.xuejiai.aaf.common.enums.elder;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 预警通知接收方类型。 */
@Getter
@AllArgsConstructor
public enum AlertRecipientTypeEnum implements ArrayValuable<String> {
    DUTY_USER("DUTY_USER", "运营值班账号"),
    CONTACT("CONTACT", "长者联系人");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(AlertRecipientTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
