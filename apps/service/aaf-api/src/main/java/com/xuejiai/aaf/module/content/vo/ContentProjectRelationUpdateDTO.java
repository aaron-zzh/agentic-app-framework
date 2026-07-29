package com.xuejiai.aaf.module.content.vo;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.framework.crud.definition.Patch;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/**
 * 项目关系更新请求。
 *
 * @author AaronZZH & Kiro
 */
public record ContentProjectRelationUpdateDTO(
        Patch<Long> projectId,
        Patch<String> relationType,
        Patch<String> layer,
        Patch<Long> sourceObjectId,
        Patch<Long> targetObjectId,
        Patch<Map<String, Object>> relationMeta,
        @NotNull @PositiveOrZero Integer expectedVersion) {

    public ContentProjectRelationUpdateDTO {
        projectId = normalize(projectId);
        relationType = normalize(relationType);
        layer = normalize(layer);
        sourceObjectId = normalize(sourceObjectId);
        targetObjectId = normalize(targetObjectId);
        relationMeta = normalize(relationMeta);
    }

    @JsonCreator
    public static ContentProjectRelationUpdateDTO fromJson(
            @JsonProperty("projectId") JsonNode projectId,
            @JsonProperty("relationType") JsonNode relationType,
            @JsonProperty("layer") JsonNode layer,
            @JsonProperty("sourceObjectId") JsonNode sourceObjectId,
            @JsonProperty("targetObjectId") JsonNode targetObjectId,
            @JsonProperty("relationMeta") JsonNode relationMeta,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new ContentProjectRelationUpdateDTO(
                Patch.parse(projectId, ContentPatchDecoder::longValue),
                Patch.parse(relationType, ContentPatchDecoder::text),
                Patch.parse(layer, ContentPatchDecoder::text),
                Patch.parse(sourceObjectId, ContentPatchDecoder::longValue),
                Patch.parse(targetObjectId, ContentPatchDecoder::longValue),
                Patch.parse(relationMeta, ContentPatchDecoder::objectMap),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
