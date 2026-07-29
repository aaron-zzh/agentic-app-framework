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
 * 项目蓝图更新请求。
 *
 * @author AaronZZH & Kiro
 */
public record ContentProjectBlueprintUpdateDTO(
        Patch<String> code,
        Patch<String> name,
        Patch<String> projectTypeCode,
        Patch<String> blueprintVersion,
        Patch<String> productionMode,
        Patch<String> description,
        Patch<String> status,
        Patch<Map<String, Object>> objectSpec,
        Patch<Map<String, Object>> relationSpec,
        Patch<Map<String, Object>> deliverableSpec,
        Patch<List<String>> actionKeys,
        Patch<List<String>> confirmationGates,
        Patch<List<String>> briefFields,
        @NotNull @PositiveOrZero Integer expectedVersion) {

    public ContentProjectBlueprintUpdateDTO {
        code = normalize(code);
        name = normalize(name);
        projectTypeCode = normalize(projectTypeCode);
        blueprintVersion = normalize(blueprintVersion);
        productionMode = normalize(productionMode);
        description = normalize(description);
        status = normalize(status);
        objectSpec = normalize(objectSpec);
        relationSpec = normalize(relationSpec);
        deliverableSpec = normalize(deliverableSpec);
        actionKeys = normalize(actionKeys);
        confirmationGates = normalize(confirmationGates);
        briefFields = normalize(briefFields);
    }

    @JsonCreator
    public static ContentProjectBlueprintUpdateDTO fromJson(
            @JsonProperty("code") JsonNode code,
            @JsonProperty("name") JsonNode name,
            @JsonProperty("projectTypeCode") JsonNode projectTypeCode,
            @JsonProperty("blueprintVersion") JsonNode blueprintVersion,
            @JsonProperty("productionMode") JsonNode productionMode,
            @JsonProperty("description") JsonNode description,
            @JsonProperty("status") JsonNode status,
            @JsonProperty("objectSpec") JsonNode objectSpec,
            @JsonProperty("relationSpec") JsonNode relationSpec,
            @JsonProperty("deliverableSpec") JsonNode deliverableSpec,
            @JsonProperty("actionKeys") JsonNode actionKeys,
            @JsonProperty("confirmationGates") JsonNode confirmationGates,
            @JsonProperty("briefFields") JsonNode briefFields,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new ContentProjectBlueprintUpdateDTO(
                Patch.parse(code, ContentPatchDecoder::text),
                Patch.parse(name, ContentPatchDecoder::text),
                Patch.parse(projectTypeCode, ContentPatchDecoder::text),
                Patch.parse(blueprintVersion, ContentPatchDecoder::text),
                Patch.parse(productionMode, ContentPatchDecoder::text),
                Patch.parse(description, ContentPatchDecoder::text),
                Patch.parse(status, ContentPatchDecoder::text),
                Patch.parse(objectSpec, ContentPatchDecoder::objectMap),
                Patch.parse(relationSpec, ContentPatchDecoder::objectMap),
                Patch.parse(deliverableSpec, ContentPatchDecoder::objectMap),
                Patch.parse(actionKeys, ContentPatchDecoder::stringList),
                Patch.parse(confirmationGates, ContentPatchDecoder::stringList),
                Patch.parse(briefFields, ContentPatchDecoder::stringList),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
