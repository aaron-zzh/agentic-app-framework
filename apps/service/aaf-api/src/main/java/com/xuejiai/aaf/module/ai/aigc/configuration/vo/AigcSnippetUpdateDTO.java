package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.framework.crud.definition.Patch;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/** 创作片段 Patch 更新请求。 */
public record AigcSnippetUpdateDTO(
        Patch<String> name,
        Patch<String> category,
        Patch<String> content,
        Patch<String> coverUrl,
        Patch<List<Long>> referenceMediaVersionIds,
        Patch<Map<String, Object>> variableSlots,
        Patch<String> projectTypeCode,
        Patch<Long> brandProfileId,
        Patch<Integer> useCount,
        Patch<Boolean> isPublic,
        @NotNull @PositiveOrZero Integer expectedVersion) {

    public AigcSnippetUpdateDTO {
        name = normalize(name);
        category = normalize(category);
        content = normalize(content);
        coverUrl = normalize(coverUrl);
        referenceMediaVersionIds = normalize(referenceMediaVersionIds);
        variableSlots = normalize(variableSlots);
        projectTypeCode = normalize(projectTypeCode);
        brandProfileId = normalize(brandProfileId);
        useCount = normalize(useCount);
        isPublic = normalize(isPublic);
    }

    @JsonCreator
    public static AigcSnippetUpdateDTO fromJson(
            @JsonProperty("name") JsonNode name,
            @JsonProperty("category") JsonNode category,
            @JsonProperty("content") JsonNode content,
            @JsonProperty("coverUrl") JsonNode coverUrl,
            @JsonProperty("referenceMediaVersionIds") JsonNode referenceMediaVersionIds,
            @JsonProperty("variableSlots") JsonNode variableSlots,
            @JsonProperty("projectTypeCode") JsonNode projectTypeCode,
            @JsonProperty("brandProfileId") JsonNode brandProfileId,
            @JsonProperty("useCount") JsonNode useCount,
            @JsonProperty("isPublic") JsonNode isPublic,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new AigcSnippetUpdateDTO(
                Patch.parse(name, AigcConfigurationPatchDecoder::text),
                Patch.parse(category, AigcConfigurationPatchDecoder::text),
                Patch.parse(content, AigcConfigurationPatchDecoder::text),
                Patch.parse(coverUrl, AigcConfigurationPatchDecoder::text),
                Patch.parse(referenceMediaVersionIds, AigcConfigurationPatchDecoder::longList),
                Patch.parse(variableSlots, AigcConfigurationPatchDecoder::objectMap),
                Patch.parse(projectTypeCode, AigcConfigurationPatchDecoder::text),
                Patch.parse(brandProfileId, AigcConfigurationPatchDecoder::longValue),
                Patch.parse(useCount, AigcConfigurationPatchDecoder::integer),
                Patch.parse(isPublic, AigcConfigurationPatchDecoder::bool),
                expectedVersion);
    }

    @JsonIgnore
    @AssertTrue(message = "coverUrl 长度不能超过 1000")
    public boolean isCoverUrlValid() {
        var value = coverUrl.valueOrNull();
        return value == null || value.length() <= 1000;
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
