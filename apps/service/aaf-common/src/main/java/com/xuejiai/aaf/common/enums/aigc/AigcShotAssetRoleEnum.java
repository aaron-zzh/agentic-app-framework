package com.xuejiai.aaf.common.enums.aigc;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 分镜素材角色枚举，对应字典 aigc_shot_asset_role。 */
@Getter
@AllArgsConstructor
public enum AigcShotAssetRoleEnum implements ArrayValuable<String> {
    FINAL_VIDEO("FINAL_VIDEO", "最终视频"),
    FINAL_AUDIO("FINAL_AUDIO", "最终音频"),
    REFERENCE("REFERENCE", "参考图");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(AigcShotAssetRoleEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
