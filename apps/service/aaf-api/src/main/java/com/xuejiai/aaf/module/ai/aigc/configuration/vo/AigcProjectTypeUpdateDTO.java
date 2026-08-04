package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

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
public record AigcProjectTypeUpdateDTO(
        Patch<String> code,
        Patch<String> name,
        Patch<String> icon,
        Patch<String> description,
        Patch<String> briefPlaceholder,
        Patch<String> definitionVersion,
        Patch<List<String>> defaultChannels,
        Patch<String> defaultProductionMode,
        Patch<Boolean> quickEntry,
        Patch<Integer> sortOrder,
        @NotNull @PositiveOrZero Integer expectedVersion) {

    public AigcProjectTypeUpdateDTO {
        code = normalize(code);
        name = normalize(name);
        icon = normalize(icon);
        description = normalize(description);
        briefPlaceholder = normalize(briefPlaceholder);
        definitionVersion = normalize(definitionVersion);
        defaultChannels = normalize(defaultChannels);
        defaultProductionMode = normalize(defaultProductionMode);
        quickEntry = normalize(quickEntry);
        sortOrder = normalize(sortOrder);
    }

    @JsonCreator
    public static AigcProjectTypeUpdateDTO fromJson(
            @JsonProperty("code") JsonNode code,
            @JsonProperty("name") JsonNode name,
            @JsonProperty("icon") JsonNode icon,
            @JsonProperty("description") JsonNode description,
            @JsonProperty("briefPlaceholder") JsonNode briefPlaceholder,
            @JsonProperty("definitionVersion") JsonNode definitionVersion,
            @JsonProperty("defaultChannels") JsonNode defaultChannels,
            @JsonProperty("defaultProductionMode") JsonNode defaultProductionMode,
            @JsonProperty("quickEntry") JsonNode quickEntry,
            @JsonProperty("sortOrder") JsonNode sortOrder,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new AigcProjectTypeUpdateDTO(
                Patch.parse(code, AigcConfigurationPatchDecoder::text),
                Patch.parse(name, AigcConfigurationPatchDecoder::text),
                Patch.parse(icon, AigcConfigurationPatchDecoder::text),
                Patch.parse(description, AigcConfigurationPatchDecoder::text),
                Patch.parse(briefPlaceholder, AigcConfigurationPatchDecoder::text),
                Patch.parse(definitionVersion, AigcConfigurationPatchDecoder::text),
                Patch.parse(defaultChannels, AigcConfigurationPatchDecoder::stringList),
                Patch.parse(defaultProductionMode, AigcConfigurationPatchDecoder::text),
                Patch.parse(quickEntry, AigcConfigurationPatchDecoder::bool),
                Patch.parse(sortOrder, AigcConfigurationPatchDecoder::integer),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
