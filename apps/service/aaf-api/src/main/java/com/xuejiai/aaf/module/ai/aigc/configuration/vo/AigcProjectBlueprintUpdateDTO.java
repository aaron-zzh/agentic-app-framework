package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

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
public record AigcProjectBlueprintUpdateDTO(
        Patch<String> code,
        Patch<String> name,
        Patch<String> projectTypeCode,
        Patch<String> blueprintVersion,
        Patch<String> productionMode,
        Patch<String> description,
        Patch<Map<String, Object>> objectSpec,
        Patch<Map<String, Object>> relationSpec,
        Patch<Map<String, Object>> deliverableSpec,
        Patch<List<String>> actionKeys,
        Patch<List<String>> confirmationGates,
        Patch<List<String>> briefFields,
        @NotNull @PositiveOrZero Integer expectedVersion) {

    public AigcProjectBlueprintUpdateDTO {
        code = normalize(code);
        name = normalize(name);
        projectTypeCode = normalize(projectTypeCode);
        blueprintVersion = normalize(blueprintVersion);
        productionMode = normalize(productionMode);
        description = normalize(description);
        objectSpec = normalize(objectSpec);
        relationSpec = normalize(relationSpec);
        deliverableSpec = normalize(deliverableSpec);
        actionKeys = normalize(actionKeys);
        confirmationGates = normalize(confirmationGates);
        briefFields = normalize(briefFields);
    }

    @JsonCreator
    public static AigcProjectBlueprintUpdateDTO fromJson(
            @JsonProperty("code") JsonNode code,
            @JsonProperty("name") JsonNode name,
            @JsonProperty("projectTypeCode") JsonNode projectTypeCode,
            @JsonProperty("blueprintVersion") JsonNode blueprintVersion,
            @JsonProperty("productionMode") JsonNode productionMode,
            @JsonProperty("description") JsonNode description,
            @JsonProperty("objectSpec") JsonNode objectSpec,
            @JsonProperty("relationSpec") JsonNode relationSpec,
            @JsonProperty("deliverableSpec") JsonNode deliverableSpec,
            @JsonProperty("actionKeys") JsonNode actionKeys,
            @JsonProperty("confirmationGates") JsonNode confirmationGates,
            @JsonProperty("briefFields") JsonNode briefFields,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new AigcProjectBlueprintUpdateDTO(
                Patch.parse(code, AigcConfigurationPatchDecoder::text),
                Patch.parse(name, AigcConfigurationPatchDecoder::text),
                Patch.parse(projectTypeCode, AigcConfigurationPatchDecoder::text),
                Patch.parse(blueprintVersion, AigcConfigurationPatchDecoder::text),
                Patch.parse(productionMode, AigcConfigurationPatchDecoder::text),
                Patch.parse(description, AigcConfigurationPatchDecoder::text),
                Patch.parse(objectSpec, AigcConfigurationPatchDecoder::objectMap),
                Patch.parse(relationSpec, AigcConfigurationPatchDecoder::objectMap),
                Patch.parse(deliverableSpec, AigcConfigurationPatchDecoder::objectMap),
                Patch.parse(actionKeys, AigcConfigurationPatchDecoder::stringList),
                Patch.parse(confirmationGates, AigcConfigurationPatchDecoder::stringList),
                Patch.parse(briefFields, AigcConfigurationPatchDecoder::stringList),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
