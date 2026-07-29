package com.xuejiai.aaf.module.content.vo;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.framework.crud.definition.Patch;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/**
 * 品牌/IP 资料更新请求。
 *
 * @author AaronZZH & Kiro
 */
public record ContentBrandProfileUpdateDTO(
        Patch<String> name,
        Patch<String> kind,
        Patch<String> industry,
        Patch<String> logoUrl,
        Patch<String> positioning,
        Patch<String> audience,
        Patch<String> toneOfVoice,
        Patch<String> visualStyle,
        Patch<String> disclaimer,
        Patch<String> forbiddenItems,
        Patch<Map<String, Object>> rules,
        Patch<Map<String, Object>> profileAssets,
        Patch<String> profileVersion,
        Patch<String> status,
        @NotNull @PositiveOrZero Integer expectedVersion) {

    public ContentBrandProfileUpdateDTO {
        name = normalize(name);
        kind = normalize(kind);
        industry = normalize(industry);
        logoUrl = normalize(logoUrl);
        positioning = normalize(positioning);
        audience = normalize(audience);
        toneOfVoice = normalize(toneOfVoice);
        visualStyle = normalize(visualStyle);
        disclaimer = normalize(disclaimer);
        forbiddenItems = normalize(forbiddenItems);
        rules = normalize(rules);
        profileAssets = normalize(profileAssets);
        profileVersion = normalize(profileVersion);
        status = normalize(status);
    }

    @JsonCreator
    public static ContentBrandProfileUpdateDTO fromJson(
            @JsonProperty("name") JsonNode name,
            @JsonProperty("kind") JsonNode kind,
            @JsonProperty("industry") JsonNode industry,
            @JsonProperty("logoUrl") JsonNode logoUrl,
            @JsonProperty("positioning") JsonNode positioning,
            @JsonProperty("audience") JsonNode audience,
            @JsonProperty("toneOfVoice") JsonNode toneOfVoice,
            @JsonProperty("visualStyle") JsonNode visualStyle,
            @JsonProperty("disclaimer") JsonNode disclaimer,
            @JsonProperty("forbiddenItems") JsonNode forbiddenItems,
            @JsonProperty("rules") JsonNode rules,
            @JsonProperty("profileAssets") JsonNode profileAssets,
            @JsonProperty("profileVersion") JsonNode profileVersion,
            @JsonProperty("status") JsonNode status,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new ContentBrandProfileUpdateDTO(
                Patch.parse(name, ContentPatchDecoder::text),
                Patch.parse(kind, ContentPatchDecoder::text),
                Patch.parse(industry, ContentPatchDecoder::text),
                Patch.parse(logoUrl, ContentPatchDecoder::text),
                Patch.parse(positioning, ContentPatchDecoder::text),
                Patch.parse(audience, ContentPatchDecoder::text),
                Patch.parse(toneOfVoice, ContentPatchDecoder::text),
                Patch.parse(visualStyle, ContentPatchDecoder::text),
                Patch.parse(disclaimer, ContentPatchDecoder::text),
                Patch.parse(forbiddenItems, ContentPatchDecoder::text),
                Patch.parse(rules, ContentPatchDecoder::objectMap),
                Patch.parse(profileAssets, ContentPatchDecoder::objectMap),
                Patch.parse(profileVersion, ContentPatchDecoder::text),
                Patch.parse(status, ContentPatchDecoder::text),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
