package com.xuejiai.aaf.common.enums.content;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 品牌/IP 资料类型。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@AllArgsConstructor
public enum ContentBrandProfileKindEnum implements ArrayValuable<String> {
    ENTERPRISE("enterprise", "企业品牌"),
    SUB_BRAND("sub_brand", "子品牌"),
    PRODUCT_LINE("product_line", "产品线"),
    PERSONAL_IP("personal_ip", "个人 IP");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values())
                    .map(ContentBrandProfileKindEnum::getCode)
                    .toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
