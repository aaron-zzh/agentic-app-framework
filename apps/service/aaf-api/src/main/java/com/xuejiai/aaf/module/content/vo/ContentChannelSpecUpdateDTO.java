package com.xuejiai.aaf.module.content.vo;

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
public record ContentChannelSpecUpdateDTO(
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
        Patch<String> status,
        @NotNull @PositiveOrZero Integer expectedVersion) {

    public ContentChannelSpecUpdateDTO {
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
        status = normalize(status);
    }

    @JsonCreator
    public static ContentChannelSpecUpdateDTO fromJson(
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
            @JsonProperty("status") JsonNode status,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new ContentChannelSpecUpdateDTO(
                Patch.parse(code, ContentPatchDecoder::text),
                Patch.parse(name, ContentPatchDecoder::text),
                Patch.parse(specVersion, ContentPatchDecoder::text),
                Patch.parse(aspectRatio, ContentPatchDecoder::text),
                Patch.parse(width, ContentPatchDecoder::integer),
                Patch.parse(height, ContentPatchDecoder::integer),
                Patch.parse(maxDurationSeconds, ContentPatchDecoder::integer),
                Patch.parse(copyStructure, ContentPatchDecoder::objectMap),
                Patch.parse(requiredDisclaimers, ContentPatchDecoder::text),
                Patch.parse(exportFormat, ContentPatchDecoder::text),
                Patch.parse(sortOrder, ContentPatchDecoder::integer),
                Patch.parse(status, ContentPatchDecoder::text),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
