package com.xuejiai.aaf.module.digitalhuman.dto;

import java.util.List;

import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "更新数字人请求")
public class UpdateDigitalHumanRequest {

    @Size(max = 255, message = "名称长度不能超过 255")
    @Schema(description = "数字人名称")
    private String name;

    @Size(max = 1000, message = "描述长度不能超过 1000")
    @Schema(description = "数字人描述")
    private String description;

    @Schema(description = "头像图片 URL")
    private String avatarImageUrl;

    @Schema(description = "标签")
    private List<String> tags;

    @Schema(description = "形象信息")
    private AppearanceConfig appearance;

    @Schema(description = "音色配置")
    private VoiceConfig voiceConfig;

    @Schema(description = "模型配置")
    private ModelConfig modelConfig;

    @Schema(description = "是否同步助理模型配置")
    private Boolean syncModelConfig;

    @Data
    @Schema(description = "形象配置")
    public static class AppearanceConfig {
        @Schema(description = "年龄")
        private Integer age;

        @Schema(description = "性别")
        private String gender;

        @Schema(description = "风格")
        private String style;

        @Schema(description = "提示词")
        private String prompt;
    }

    @Data
    @Schema(description = "音色配置")
    public static class VoiceConfig {
        @Schema(description = "提供商")
        private String provider;

        @Schema(description = "预设音色 ID")
        private Long presetVoiceId;

        @Schema(description = "音色名称")
        private String voiceName;

        @Schema(description = "语速（-500 ~ 500）")
        private Integer speechRate;

        @Schema(description = "音调（-500 ~ 500）")
        private Integer pitchRate;

        @Schema(description = "音量（0 ~ 100）")
        private Integer volume;
    }

    @Data
    @Schema(description = "模型配置")
    public static class ModelConfig {
        @Schema(description = "提供商")
        private String provider;

        @Schema(description = "模型名称")
        private String model;

        @Schema(description = "温度")
        private Double temperature;

        @Schema(description = "最大 Token 数")
        private Integer maxTokens;

        @Schema(description = "系统提示词")
        private String systemPrompt;
    }
}
