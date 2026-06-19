package com.xuejiai.aaf.common.enums.developer;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DeveloperRedeemCodeStatusEnum implements ArrayValuable<String> {
    UNUSED("UNUSED", "未使用"),
    REDEEMED("REDEEMED", "已兑换"),
    EXPIRED("EXPIRED", "已过期");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values())
                    .map(DeveloperRedeemCodeStatusEnum::getCode)
                    .toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
