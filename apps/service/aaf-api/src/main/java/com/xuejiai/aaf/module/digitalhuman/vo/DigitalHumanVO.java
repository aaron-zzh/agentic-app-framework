package com.xuejiai.aaf.module.digitalhuman.vo;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "数字人视图对象")
public class DigitalHumanVO {

    @Schema(description = "数字人 ID")
    private Long id;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "描述")
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

    @Schema(description = "关联的助理 ID")
    private Long assistantId;

    @Schema(description = "是否同步助理模型配置")
    private Boolean syncModelConfig;

    @Schema(description = "生成视频数量")
    private Integer videoCount;

    @Schema(description = "累计视频时长（秒）")
    private Integer totalDuration;

    @Schema(description = "累计消耗积分")
    private Integer creditsConsumed;

    @Schema(description = "最后使用时间")
    private LocalDateTime lastUsedAt;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "生成来源")
    private String generationSource;

    @Schema(description = "关联的 AIGC 项目 ID")
    private Long aigcProjectId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

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
