package com.xuejiai.aaf.common.enums.billing;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 会员等级编码 */
@Getter
@AllArgsConstructor
public enum LevelCodeEnum implements ArrayValuable<String> {
    L0("L0", "普通会员"),
    L1("L1", "银牌会员"),
    L2("L2", "金牌会员");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(LevelCodeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
