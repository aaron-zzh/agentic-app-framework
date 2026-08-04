package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.framework.crud.definition.Patch;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/**
 * 渠道规格更新请求。
 *
 * @author AaronZZH & Kiro
 */
public record AigcChannelSpecUpdateDTO(
        Patch<String> code,
        Patch<String> name,
        Patch<String> specVersion,
        Patch<String> aspectRatio,
        Patch<Integer> width,
        Patch<Integer> height,
        Patch<Integer> maxDurationSeconds,
        Patch<Map<String, Object>> copyStructure,
        Patch<String> requiredDisclaimers,
        Patch<String> exportFormat,
        Patch<Integer> sortOrder,
        @NotNull @PositiveOrZero Integer expectedVersion) {

    public AigcChannelSpecUpdateDTO {
        code = normalize(code);
        name = normalize(name);
        specVersion = normalize(specVersion);
        aspectRatio = normalize(aspectRatio);
        width = normalize(width);
        height = normalize(height);
        maxDurationSeconds = normalize(maxDurationSeconds);
        copyStructure = normalize(copyStructure);
        requiredDisclaimers = normalize(requiredDisclaimers);
        exportFormat = normalize(exportFormat);
        sortOrder = normalize(sortOrder);
    }

    @JsonCreator
    public static AigcChannelSpecUpdateDTO fromJson(
            @JsonProperty("code") JsonNode code,
            @JsonProperty("name") JsonNode name,
            @JsonProperty("specVersion") JsonNode specVersion,
            @JsonProperty("aspectRatio") JsonNode aspectRatio,
            @JsonProperty("width") JsonNode width,
            @JsonProperty("height") JsonNode height,
            @JsonProperty("maxDurationSeconds") JsonNode maxDurationSeconds,
            @JsonProperty("copyStructure") JsonNode copyStructure,
            @JsonProperty("requiredDisclaimers") JsonNode requiredDisclaimers,
            @JsonProperty("exportFormat") JsonNode exportFormat,
            @JsonProperty("sortOrder") JsonNode sortOrder,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new AigcChannelSpecUpdateDTO(
                Patch.parse(code, AigcConfigurationPatchDecoder::text),
                Patch.parse(name, AigcConfigurationPatchDecoder::text),
                Patch.parse(specVersion, AigcConfigurationPatchDecoder::text),
                Patch.parse(aspectRatio, AigcConfigurationPatchDecoder::text),
                Patch.parse(width, AigcConfigurationPatchDecoder::integer),
                Patch.parse(height, AigcConfigurationPatchDecoder::integer),
                Patch.parse(maxDurationSeconds, AigcConfigurationPatchDecoder::integer),
                Patch.parse(copyStructure, AigcConfigurationPatchDecoder::objectMap),
                Patch.parse(requiredDisclaimers, AigcConfigurationPatchDecoder::text),
                Patch.parse(exportFormat, AigcConfigurationPatchDecoder::text),
                Patch.parse(sortOrder, AigcConfigurationPatchDecoder::integer),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
