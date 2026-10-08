package com.xuejiai.aaf.module.digitalhuman.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/** 预设音色分页查询。 */
@Getter
@Setter
@Schema(description = "预设音色分页查询")
public class PresetVoicePageDTO extends PageParam {

    @Schema(description = "是否启用")
    private Boolean isActive;

    @Schema(description = "提供商")
    private String provider;

    @Schema(description = "性别")
    private String gender;
}
