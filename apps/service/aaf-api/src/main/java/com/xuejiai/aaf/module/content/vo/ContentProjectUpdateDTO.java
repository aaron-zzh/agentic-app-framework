package com.xuejiai.aaf.module.content.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.framework.crud.definition.Patch;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/**
 * 内容项目更新请求。
 *
 * @author AaronZZH & Kiro
 */
public record ContentProjectUpdateDTO(
        Patch<String> name,
        Patch<String> projectTypeCode,
        Patch<String> blueprintCode,
        Patch<String> blueprintVersion,
        Patch<String> domainExtensionCode,
        Patch<String> domainExtensionVersion,
        Patch<String> productionMode,
        Patch<String> generationMode,
        Patch<String> status,
        Patch<String> brief,
        Patch<String> coverUrl,
        Patch<List<String>> channels,
        Patch<Map<String, Object>> configSnapshot,
        Patch<Integer> graphRevision,
        Patch<Long> primaryBrandProfileId,
        Patch<Long> assistantId,
        Patch<BigDecimal> budgetLimit,
        Patch<BigDecimal> costUsed,
        Patch<LocalDateTime> lastActiveTime,
        @NotNull @PositiveOrZero Integer expectedVersion) {

    public ContentProjectUpdateDTO {
        name = normalize(name);
        projectTypeCode = normalize(projectTypeCode);
        blueprintCode = normalize(blueprintCode);
        blueprintVersion = normalize(blueprintVersion);
        domainExtensionCode = normalize(domainExtensionCode);
        domainExtensionVersion = normalize(domainExtensionVersion);
        productionMode = normalize(productionMode);
        generationMode = normalize(generationMode);
        status = normalize(status);
        brief = normalize(brief);
        coverUrl = normalize(coverUrl);
        channels = normalize(channels);
        configSnapshot = normalize(configSnapshot);
        graphRevision = normalize(graphRevision);
        primaryBrandProfileId = normalize(primaryBrandProfileId);
        assistantId = normalize(assistantId);
        budgetLimit = normalize(budgetLimit);
        costUsed = normalize(costUsed);
        lastActiveTime = normalize(lastActiveTime);
    }

    @JsonCreator
    public static ContentProjectUpdateDTO fromJson(
            @JsonProperty("name") JsonNode name,
            @JsonProperty("projectTypeCode") JsonNode projectTypeCode,
            @JsonProperty("blueprintCode") JsonNode blueprintCode,
            @JsonProperty("blueprintVersion") JsonNode blueprintVersion,
            @JsonProperty("domainExtensionCode") JsonNode domainExtensionCode,
            @JsonProperty("domainExtensionVersion") JsonNode domainExtensionVersion,
            @JsonProperty("productionMode") JsonNode productionMode,
            @JsonProperty("generationMode") JsonNode generationMode,
            @JsonProperty("status") JsonNode status,
            @JsonProperty("brief") JsonNode brief,
            @JsonProperty("coverUrl") JsonNode coverUrl,
            @JsonProperty("channels") JsonNode channels,
            @JsonProperty("configSnapshot") JsonNode configSnapshot,
            @JsonProperty("graphRevision") JsonNode graphRevision,
            @JsonProperty("primaryBrandProfileId") JsonNode primaryBrandProfileId,
            @JsonProperty("assistantId") JsonNode assistantId,
            @JsonProperty("budgetLimit") JsonNode budgetLimit,
            @JsonProperty("costUsed") JsonNode costUsed,
            @JsonProperty("lastActiveTime") JsonNode lastActiveTime,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new ContentProjectUpdateDTO(
                Patch.parse(name, ContentPatchDecoder::text),
                Patch.parse(projectTypeCode, ContentPatchDecoder::text),
                Patch.parse(blueprintCode, ContentPatchDecoder::text),
                Patch.parse(blueprintVersion, ContentPatchDecoder::text),
                Patch.parse(domainExtensionCode, ContentPatchDecoder::text),
                Patch.parse(domainExtensionVersion, ContentPatchDecoder::text),
                Patch.parse(productionMode, ContentPatchDecoder::text),
                Patch.parse(generationMode, ContentPatchDecoder::text),
                Patch.parse(status, ContentPatchDecoder::text),
                Patch.parse(brief, ContentPatchDecoder::text),
                Patch.parse(coverUrl, ContentPatchDecoder::text),
                Patch.parse(channels, ContentPatchDecoder::stringList),
                Patch.parse(configSnapshot, ContentPatchDecoder::objectMap),
                Patch.parse(graphRevision, ContentPatchDecoder::integer),
                Patch.parse(primaryBrandProfileId, ContentPatchDecoder::longValue),
                Patch.parse(assistantId, ContentPatchDecoder::longValue),
                Patch.parse(budgetLimit, ContentPatchDecoder::decimal),
                Patch.parse(costUsed, ContentPatchDecoder::decimal),
                Patch.parse(lastActiveTime, ContentPatchDecoder::dateTime),
                expectedVersion);
    }

    public static ContentProjectUpdateDTO statusPatch(String status, Integer expectedVersion) {
        return new ContentProjectUpdateDTO(
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.value(status),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
