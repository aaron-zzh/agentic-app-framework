package com.xuejiai.aaf.module.content.vo;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.framework.crud.definition.Patch;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/**
 * 创作片段更新请求。
 *
 * @author AaronZZH & Kiro
 */
public record ContentSnippetUpdateDTO(
        Patch<String> name,
        Patch<String> category,
        Patch<String> content,
        Patch<List<String>> referenceImageUrls,
        Patch<Map<String, Object>> variableSlots,
        Patch<String> projectTypeCode,
        Patch<Long> brandProfileId,
        Patch<Integer> useCount,
        Patch<Boolean> isPublic,
        @NotNull @PositiveOrZero Integer expectedVersion) {

    public ContentSnippetUpdateDTO {
        name = normalize(name);
        category = normalize(category);
        content = normalize(content);
        referenceImageUrls = normalize(referenceImageUrls);
        variableSlots = normalize(variableSlots);
        projectTypeCode = normalize(projectTypeCode);
        brandProfileId = normalize(brandProfileId);
        useCount = normalize(useCount);
        isPublic = normalize(isPublic);
    }

    @JsonCreator
    public static ContentSnippetUpdateDTO fromJson(
            @JsonProperty("name") JsonNode name,
            @JsonProperty("category") JsonNode category,
            @JsonProperty("content") JsonNode content,
            @JsonProperty("referenceImageUrls") JsonNode referenceImageUrls,
            @JsonProperty("variableSlots") JsonNode variableSlots,
            @JsonProperty("projectTypeCode") JsonNode projectTypeCode,
            @JsonProperty("brandProfileId") JsonNode brandProfileId,
            @JsonProperty("useCount") JsonNode useCount,
            @JsonProperty("isPublic") JsonNode isPublic,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new ContentSnippetUpdateDTO(
                Patch.parse(name, ContentPatchDecoder::text),
                Patch.parse(category, ContentPatchDecoder::text),
                Patch.parse(content, ContentPatchDecoder::text),
                Patch.parse(referenceImageUrls, ContentPatchDecoder::stringList),
                Patch.parse(variableSlots, ContentPatchDecoder::objectMap),
                Patch.parse(projectTypeCode, ContentPatchDecoder::text),
                Patch.parse(brandProfileId, ContentPatchDecoder::longValue),
                Patch.parse(useCount, ContentPatchDecoder::integer),
                Patch.parse(isPublic, ContentPatchDecoder::bool),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
