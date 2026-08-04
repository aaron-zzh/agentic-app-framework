package com.xuejiai.aaf.module.ai.aigc.brand.enums;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** AIGC 品牌/IP 资料类型。 */
@Getter
@AllArgsConstructor
public enum AigcBrandProfileKind implements ArrayValuable<String> {
    ENTERPRISE("enterprise", "企业品牌"),
    SUB_BRAND("sub_brand", "子品牌"),
    PRODUCT_LINE("product_line", "产品线"),
    PERSONAL_IP("personal_ip", "个人 IP");

    private final String code;
    private final String label;

    private static final String[] VALUES =
            Arrays.stream(values()).map(AigcBrandProfileKind::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return VALUES;
    }
}
