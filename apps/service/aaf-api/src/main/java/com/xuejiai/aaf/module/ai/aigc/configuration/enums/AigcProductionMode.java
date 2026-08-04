package com.xuejiai.aaf.module.ai.aigc.configuration.enums;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** AIGC 内容生产模式。 */
@Getter
@AllArgsConstructor
public enum AigcProductionMode implements ArrayValuable<String> {
    STANDARD("standard", "标准"),
    SHORT_DRAMA("short_drama", "短剧"),
    MOTION_COMIC("motion_comic", "漫剧");

    private final String code;
    private final String label;

    private static final String[] VALUES =
            Arrays.stream(values()).map(AigcProductionMode::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return VALUES;
    }
}
