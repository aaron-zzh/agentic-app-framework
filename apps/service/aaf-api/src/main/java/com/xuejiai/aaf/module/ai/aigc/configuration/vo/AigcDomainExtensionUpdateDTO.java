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
 * 行业扩展更新请求。
 *
 * @author AaronZZH & Kiro
 */
public record AigcDomainExtensionUpdateDTO(
        Patch<String> code,
        Patch<String> name,
        Patch<String> extensionVersion,
        Patch<String> industry,
        Patch<String> region,
        Patch<String> language,
        Patch<Map<String, Object>> profileSchemaExt,
        Patch<Map<String, Object>> objectDefinitions,
        Patch<Map<String, Object>> knowledgeRequirements,
        Patch<Map<String, Object>> ruleSets,
        Patch<List<String>> validators,
        Patch<List<String>> roleRecommendations,
        Patch<Map<String, Object>> actionConstraints,
        Patch<Map<String, Object>> channelOverrides,
        Patch<Map<String, Object>> migrationDeclaration,
        @NotNull @PositiveOrZero Integer expectedVersion) {

    public AigcDomainExtensionUpdateDTO {
        code = normalize(code);
        name = normalize(name);
        extensionVersion = normalize(extensionVersion);
        industry = normalize(industry);
        region = normalize(region);
        language = normalize(language);
        profileSchemaExt = normalize(profileSchemaExt);
        objectDefinitions = normalize(objectDefinitions);
        knowledgeRequirements = normalize(knowledgeRequirements);
        ruleSets = normalize(ruleSets);
        validators = normalize(validators);
        roleRecommendations = normalize(roleRecommendations);
        actionConstraints = normalize(actionConstraints);
        channelOverrides = normalize(channelOverrides);
        migrationDeclaration = normalize(migrationDeclaration);
    }

    @JsonCreator
    public static AigcDomainExtensionUpdateDTO fromJson(
            @JsonProperty("code") JsonNode code,
            @JsonProperty("name") JsonNode name,
            @JsonProperty("extensionVersion") JsonNode extensionVersion,
            @JsonProperty("industry") JsonNode industry,
            @JsonProperty("region") JsonNode region,
            @JsonProperty("language") JsonNode language,
            @JsonProperty("profileSchemaExt") JsonNode profileSchemaExt,
            @JsonProperty("objectDefinitions") JsonNode objectDefinitions,
            @JsonProperty("knowledgeRequirements") JsonNode knowledgeRequirements,
            @JsonProperty("ruleSets") JsonNode ruleSets,
            @JsonProperty("validators") JsonNode validators,
            @JsonProperty("roleRecommendations") JsonNode roleRecommendations,
            @JsonProperty("actionConstraints") JsonNode actionConstraints,
            @JsonProperty("channelOverrides") JsonNode channelOverrides,
            @JsonProperty("migrationDeclaration") JsonNode migrationDeclaration,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new AigcDomainExtensionUpdateDTO(
                Patch.parse(code, AigcConfigurationPatchDecoder::text),
                Patch.parse(name, AigcConfigurationPatchDecoder::text),
                Patch.parse(extensionVersion, AigcConfigurationPatchDecoder::text),
                Patch.parse(industry, AigcConfigurationPatchDecoder::text),
                Patch.parse(region, AigcConfigurationPatchDecoder::text),
                Patch.parse(language, AigcConfigurationPatchDecoder::text),
                Patch.parse(profileSchemaExt, AigcConfigurationPatchDecoder::objectMap),
                Patch.parse(objectDefinitions, AigcConfigurationPatchDecoder::objectMap),
                Patch.parse(knowledgeRequirements, AigcConfigurationPatchDecoder::objectMap),
                Patch.parse(ruleSets, AigcConfigurationPatchDecoder::objectMap),
                Patch.parse(validators, AigcConfigurationPatchDecoder::stringList),
                Patch.parse(roleRecommendations, AigcConfigurationPatchDecoder::stringList),
                Patch.parse(actionConstraints, AigcConfigurationPatchDecoder::objectMap),
                Patch.parse(channelOverrides, AigcConfigurationPatchDecoder::objectMap),
                Patch.parse(migrationDeclaration, AigcConfigurationPatchDecoder::objectMap),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
