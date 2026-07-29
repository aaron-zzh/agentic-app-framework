package com.xuejiai.aaf.module.content.vo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.framework.crud.definition.Patch;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/**
 * 项目类型更新请求。
 *
 * @author AaronZZH & Kiro
 */
public record ContentProjectTypeUpdateDTO(
        Patch<String> code,
        Patch<String> name,
        Patch<String> icon,
        Patch<String> description,
        Patch<String> briefPlaceholder,
        Patch<List<String>> defaultChannels,
        Patch<String> defaultProductionMode,
        Patch<Boolean> quickEntry,
        Patch<Boolean> builtin,
        Patch<Integer> sortOrder,
        Patch<String> status,
        @NotNull @PositiveOrZero Integer expectedVersion) {

    public ContentProjectTypeUpdateDTO {
        code = normalize(code);
        name = normalize(name);
        icon = normalize(icon);
        description = normalize(description);
        briefPlaceholder = normalize(briefPlaceholder);
        defaultChannels = normalize(defaultChannels);
        defaultProductionMode = normalize(defaultProductionMode);
        quickEntry = normalize(quickEntry);
        builtin = normalize(builtin);
        sortOrder = normalize(sortOrder);
        status = normalize(status);
    }

    @JsonCreator
    public static ContentProjectTypeUpdateDTO fromJson(
            @JsonProperty("code") JsonNode code,
            @JsonProperty("name") JsonNode name,
            @JsonProperty("icon") JsonNode icon,
            @JsonProperty("description") JsonNode description,
            @JsonProperty("briefPlaceholder") JsonNode briefPlaceholder,
            @JsonProperty("defaultChannels") JsonNode defaultChannels,
            @JsonProperty("defaultProductionMode") JsonNode defaultProductionMode,
            @JsonProperty("quickEntry") JsonNode quickEntry,
            @JsonProperty("builtin") JsonNode builtin,
            @JsonProperty("sortOrder") JsonNode sortOrder,
            @JsonProperty("status") JsonNode status,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new ContentProjectTypeUpdateDTO(
                Patch.parse(code, ContentPatchDecoder::text),
                Patch.parse(name, ContentPatchDecoder::text),
                Patch.parse(icon, ContentPatchDecoder::text),
                Patch.parse(description, ContentPatchDecoder::text),
                Patch.parse(briefPlaceholder, ContentPatchDecoder::text),
                Patch.parse(defaultChannels, ContentPatchDecoder::stringList),
                Patch.parse(defaultProductionMode, ContentPatchDecoder::text),
                Patch.parse(quickEntry, ContentPatchDecoder::bool),
                Patch.parse(builtin, ContentPatchDecoder::bool),
                Patch.parse(sortOrder, ContentPatchDecoder::integer),
                Patch.parse(status, ContentPatchDecoder::text),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
