package com.xuejiai.aaf.module.content.vo;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.framework.crud.definition.Patch;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/**
 * 项目对象更新请求。
 *
 * @author AaronZZH & Kiro
 */
public record ContentProjectObjectUpdateDTO(
        Patch<Long> projectId,
        Patch<String> objectType,
        Patch<String> objectKey,
        Patch<String> blueprintNodeKey,
        Patch<Long> parentId,
        Patch<Integer> sortOrder,
        Patch<String> title,
        Patch<String> status,
        Patch<String> source,
        Patch<String> schemaVersion,
        Patch<String> entityResource,
        Patch<Long> entityId,
        Patch<String> adoptedVersionRef,
        Patch<String> summary,
        Patch<Map<String, Object>> payload,
        @NotNull @PositiveOrZero Integer expectedVersion) {

    public ContentProjectObjectUpdateDTO {
        projectId = normalize(projectId);
        objectType = normalize(objectType);
        objectKey = normalize(objectKey);
        blueprintNodeKey = normalize(blueprintNodeKey);
        parentId = normalize(parentId);
        sortOrder = normalize(sortOrder);
        title = normalize(title);
        status = normalize(status);
        source = normalize(source);
        schemaVersion = normalize(schemaVersion);
        entityResource = normalize(entityResource);
        entityId = normalize(entityId);
        adoptedVersionRef = normalize(adoptedVersionRef);
        summary = normalize(summary);
        payload = normalize(payload);
    }

    @JsonCreator
    public static ContentProjectObjectUpdateDTO fromJson(
            @JsonProperty("projectId") JsonNode projectId,
            @JsonProperty("objectType") JsonNode objectType,
            @JsonProperty("objectKey") JsonNode objectKey,
            @JsonProperty("blueprintNodeKey") JsonNode blueprintNodeKey,
            @JsonProperty("parentId") JsonNode parentId,
            @JsonProperty("sortOrder") JsonNode sortOrder,
            @JsonProperty("title") JsonNode title,
            @JsonProperty("status") JsonNode status,
            @JsonProperty("source") JsonNode source,
            @JsonProperty("schemaVersion") JsonNode schemaVersion,
            @JsonProperty("entityResource") JsonNode entityResource,
            @JsonProperty("entityId") JsonNode entityId,
            @JsonProperty("adoptedVersionRef") JsonNode adoptedVersionRef,
            @JsonProperty("summary") JsonNode summary,
            @JsonProperty("payload") JsonNode payload,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new ContentProjectObjectUpdateDTO(
                Patch.parse(projectId, ContentPatchDecoder::longValue),
                Patch.parse(objectType, ContentPatchDecoder::text),
                Patch.parse(objectKey, ContentPatchDecoder::text),
                Patch.parse(blueprintNodeKey, ContentPatchDecoder::text),
                Patch.parse(parentId, ContentPatchDecoder::longValue),
                Patch.parse(sortOrder, ContentPatchDecoder::integer),
                Patch.parse(title, ContentPatchDecoder::text),
                Patch.parse(status, ContentPatchDecoder::text),
                Patch.parse(source, ContentPatchDecoder::text),
                Patch.parse(schemaVersion, ContentPatchDecoder::text),
                Patch.parse(entityResource, ContentPatchDecoder::text),
                Patch.parse(entityId, ContentPatchDecoder::longValue),
                Patch.parse(adoptedVersionRef, ContentPatchDecoder::text),
                Patch.parse(summary, ContentPatchDecoder::text),
                Patch.parse(payload, ContentPatchDecoder::objectMap),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
