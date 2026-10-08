package com.xuejiai.aaf.module.digitalhuman.vo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.framework.crud.definition.Patch;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/**
 * 更新数字人（Patch 语义：未传不改、null 清空、有值覆盖）。
 *
 * <p>{@code assistantId} 与 {@code status} 仅作为字段能力声明供专用命令（关联助理、归档、恢复）使用， 通用更新传入会被拒绝。
 */
@Schema(description = "更新数字人")
public record DigitalHumanUpdateDTO(
        Patch<String> name,
        Patch<String> description,
        Patch<String> avatarImageUrl,
        Patch<List<String>> tags,
        Patch<DigitalHumanAppearance> appearance,
        Patch<DigitalHumanVoiceConfig> voiceConfig,
        Patch<DigitalHumanModelConfig> modelConfig,
        Patch<Long> assistantId,
        Patch<Boolean> syncModelConfig,
        Patch<String> status,
        @NotNull @PositiveOrZero Integer expectedVersion) {

    public DigitalHumanUpdateDTO {
        name = normalize(name);
        description = normalize(description);
        avatarImageUrl = normalize(avatarImageUrl);
        tags = normalize(tags);
        appearance = normalize(appearance);
        voiceConfig = normalize(voiceConfig);
        modelConfig = normalize(modelConfig);
        assistantId = normalize(assistantId);
        syncModelConfig = normalize(syncModelConfig);
        status = normalize(status);
    }

    @JsonCreator
    public static DigitalHumanUpdateDTO fromJson(
            @JsonProperty("name") JsonNode name,
            @JsonProperty("description") JsonNode description,
            @JsonProperty("avatarImageUrl") JsonNode avatarImageUrl,
            @JsonProperty("tags") JsonNode tags,
            @JsonProperty("appearance") JsonNode appearance,
            @JsonProperty("voiceConfig") JsonNode voiceConfig,
            @JsonProperty("modelConfig") JsonNode modelConfig,
            @JsonProperty("assistantId") JsonNode assistantId,
            @JsonProperty("syncModelConfig") JsonNode syncModelConfig,
            @JsonProperty("status") JsonNode status,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new DigitalHumanUpdateDTO(
                Patch.parse(name, DigitalHumanPatchDecoder::text),
                Patch.parse(description, DigitalHumanPatchDecoder::text),
                Patch.parse(avatarImageUrl, DigitalHumanPatchDecoder::text),
                Patch.parse(tags, DigitalHumanPatchDecoder::stringList),
                Patch.parse(appearance, DigitalHumanPatchDecoder::appearance),
                Patch.parse(voiceConfig, DigitalHumanPatchDecoder::voiceConfig),
                Patch.parse(modelConfig, DigitalHumanPatchDecoder::modelConfig),
                Patch.parse(assistantId, DigitalHumanPatchDecoder::longValue),
                Patch.parse(syncModelConfig, DigitalHumanPatchDecoder::bool),
                Patch.parse(status, DigitalHumanPatchDecoder::text),
                expectedVersion);
    }

    @JsonIgnore
    @AssertTrue(message = "name 不能为空且长度不超过 255")
    public boolean isNameValid() {
        var value = name.valueOrNull();
        return value == null || (!value.isBlank() && value.length() <= 255);
    }

    @JsonIgnore
    @AssertTrue(message = "description 长度不能超过 1000")
    public boolean isDescriptionValid() {
        var value = description.valueOrNull();
        return value == null || value.length() <= 1000;
    }

    @JsonIgnore
    @AssertTrue(message = "avatarImageUrl 不能为空且长度不超过 512")
    public boolean isAvatarImageUrlValid() {
        var value = avatarImageUrl.valueOrNull();
        return value == null || (!value.isBlank() && value.length() <= 512);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
