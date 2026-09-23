package com.xuejiai.aaf.module.digitalhuman.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "预设音色视图对象")
public class PresetVoiceVO {

    @Schema(description = "音色 ID")
    private Long id;

    @Schema(description = "音色名称")
    private String name;

    @Schema(description = "音色描述")
    private String description;

    @Schema(description = "性别")
    private String gender;

    @Schema(description = "试听音频 URL")
    private String sampleAudioUrl;

    @Schema(description = "提供商")
    private String provider;

    @Schema(description = "外部音色 ID")
    private String externalVoiceId;

    @Schema(description = "显示顺序")
    private Integer displayOrder;
}
