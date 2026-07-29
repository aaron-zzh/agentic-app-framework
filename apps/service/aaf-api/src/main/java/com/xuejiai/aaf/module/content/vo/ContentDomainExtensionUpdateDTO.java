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
 * 行业扩展更新请求。
 *
 * @author AaronZZH & Kiro
 */
public record ContentDomainExtensionUpdateDTO(
        Patch<String> code,
        Patch<String> name,
        Patch<String> extensionVersion,
        Patch<String> industry,
        Patch<String> region,
        Patch<String> language,
        Patch<String> status,
        Patch<Map<String, Object>> profileSchemaExt,
        Patch<Map<String, Object>> objectDefinitions,
        Patch<Map<String, Object>> knowledgeRequirements,
        Patch<Map<String, Object>> ruleSets,
        Patch<List<String>> validators,
        Patch<List<String>> roleRecommendations,
        Patch<Map<String, Object>> actionConstraints,
        Patch<Map<String, Object>> channelOverrides,
        @NotNull @PositiveOrZero Integer expectedVersion) {

    public ContentDomainExtensionUpdateDTO {
        code = normalize(code);
        name = normalize(name);
        extensionVersion = normalize(extensionVersion);
        industry = normalize(industry);
        region = normalize(region);
        language = normalize(language);
        status = normalize(status);
        profileSchemaExt = normalize(profileSchemaExt);
        objectDefinitions = normalize(objectDefinitions);
        knowledgeRequirements = normalize(knowledgeRequirements);
        ruleSets = normalize(ruleSets);
        validators = normalize(validators);
        roleRecommendations = normalize(roleRecommendations);
        actionConstraints = normalize(actionConstraints);
        channelOverrides = normalize(channelOverrides);
    }

    @JsonCreator
    public static ContentDomainExtensionUpdateDTO fromJson(
            @JsonProperty("code") JsonNode code,
            @JsonProperty("name") JsonNode name,
            @JsonProperty("extensionVersion") JsonNode extensionVersion,
            @JsonProperty("industry") JsonNode industry,
            @JsonProperty("region") JsonNode region,
            @JsonProperty("language") JsonNode language,
            @JsonProperty("status") JsonNode status,
            @JsonProperty("profileSchemaExt") JsonNode profileSchemaExt,
            @JsonProperty("objectDefinitions") JsonNode objectDefinitions,
            @JsonProperty("knowledgeRequirements") JsonNode knowledgeRequirements,
            @JsonProperty("ruleSets") JsonNode ruleSets,
            @JsonProperty("validators") JsonNode validators,
            @JsonProperty("roleRecommendations") JsonNode roleRecommendations,
            @JsonProperty("actionConstraints") JsonNode actionConstraints,
            @JsonProperty("channelOverrides") JsonNode channelOverrides,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new ContentDomainExtensionUpdateDTO(
                Patch.parse(code, ContentPatchDecoder::text),
                Patch.parse(name, ContentPatchDecoder::text),
                Patch.parse(extensionVersion, ContentPatchDecoder::text),
                Patch.parse(industry, ContentPatchDecoder::text),
                Patch.parse(region, ContentPatchDecoder::text),
                Patch.parse(language, ContentPatchDecoder::text),
                Patch.parse(status, ContentPatchDecoder::text),
                Patch.parse(profileSchemaExt, ContentPatchDecoder::objectMap),
                Patch.parse(objectDefinitions, ContentPatchDecoder::objectMap),
                Patch.parse(knowledgeRequirements, ContentPatchDecoder::objectMap),
                Patch.parse(ruleSets, ContentPatchDecoder::objectMap),
                Patch.parse(validators, ContentPatchDecoder::stringList),
                Patch.parse(roleRecommendations, ContentPatchDecoder::stringList),
                Patch.parse(actionConstraints, ContentPatchDecoder::objectMap),
                Patch.parse(channelOverrides, ContentPatchDecoder::objectMap),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
