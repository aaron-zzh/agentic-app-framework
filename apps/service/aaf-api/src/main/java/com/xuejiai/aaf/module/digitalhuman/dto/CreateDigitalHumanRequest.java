package com.xuejiai.aaf.module.digitalhuman.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "创建数字人请求")
public class CreateDigitalHumanRequest {

    @NotBlank(message = "名称不能为空")
    @Size(max = 255, message = "名称长度不能超过 255")
    @Schema(description = "数字人名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Size(max = 1000, message = "描述长度不能超过 1000")
    @Schema(description = "数字人描述")
    private String description;

    @NotBlank(message = "头像图片 URL 不能为空")
    @Schema(description = "头像图片 URL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String avatarImageUrl;

    @Schema(description = "标签")
    private List<String> tags;

    @Schema(description = "形象信息")
    private AppearanceConfig appearance;

    @Schema(description = "音色配置", requiredMode = Schema.RequiredMode.REQUIRED)
    private VoiceConfig voiceConfig;

    @Schema(description = "模型配置")
    private ModelConfig modelConfig;

    @Schema(description = "生成来源")
    private String generationSource;

    @Schema(description = "关联的 AIGC 项目 ID")
    private Long aigcProjectId;

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
        @Schema(description = "提供商", example = "aliyun")
        private String provider = "aliyun";

        @Schema(description = "预设音色 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        private Long presetVoiceId;

        @Schema(description = "音色名称")
        private String voiceName;

        @Schema(description = "语速（-500 ~ 500）", example = "0")
        private Integer speechRate = 0;

        @Schema(description = "音调（-500 ~ 500）", example = "0")
        private Integer pitchRate = 0;

        @Schema(description = "音量（0 ~ 100）", example = "50")
        private Integer volume = 50;
    }

    @Data
    @Schema(description = "模型配置")
    public static class ModelConfig {
        @Schema(description = "提供商", example = "anthropic")
        private String provider;

        @Schema(description = "模型名称", example = "claude-sonnet-5")
        private String model;

        @Schema(description = "温度", example = "0.7")
        private Double temperature;

        @Schema(description = "最大 Token 数", example = "2000")
        private Integer maxTokens;

        @Schema(description = "系统提示词")
        private String systemPrompt;
    }
}
