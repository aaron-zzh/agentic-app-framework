package com.xuejiai.aaf.common.enums.aigc;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 内容素材角色枚举，对应字典 aigc_content_asset_role。 */
@Getter
@AllArgsConstructor
public enum AigcContentAssetRoleEnum implements ArrayValuable<String> {
    MAIN("MAIN", "正片"),
    COVER("COVER", "封面"),
    BGM("BGM", "背景音乐"),
    SUBTITLE("SUBTITLE", "字幕");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(AigcContentAssetRoleEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
