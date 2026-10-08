package com.xuejiai.aaf.module.digitalhuman.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 创建数字人。 */
@Schema(description = "创建数字人")
public record DigitalHumanCreateDTO(
        @NotBlank @Size(max = 255) @Schema(description = "数字人名称") String name,
        @Size(max = 1000) @Schema(description = "数字人描述") String description,
        @NotBlank @Size(max = 512) @Schema(description = "头像图片 URL") String avatarImageUrl,
        @Schema(description = "标签") List<String> tags,
        @Valid @Schema(description = "形象信息") DigitalHumanAppearance appearance,
        @NotNull @Valid @Schema(description = "音色配置") DigitalHumanVoiceConfig voiceConfig,
        @Valid @Schema(description = "模型配置") DigitalHumanModelConfig modelConfig,
        @Size(max = 32) @Schema(description = "生成来源") String generationSource,
        @Schema(description = "关联的 AIGC 项目 ID") Long aigcProjectId) {}
