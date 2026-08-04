package com.xuejiai.aaf.module.ai.aigc.brand.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.framework.crud.definition.Patch;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/** 更新品牌/IP 稳定身份。 */
public record AigcBrandProfileUpdateDTO(
        Patch<String> name,
        Patch<String> kind,
        Patch<String> industry,
        Patch<Long> currentVersionId,
        Patch<String> status,
        @NotNull @PositiveOrZero Integer expectedVersion) {

    public AigcBrandProfileUpdateDTO {
        name = normalize(name);
        kind = normalize(kind);
        industry = normalize(industry);
        currentVersionId = normalize(currentVersionId);
        status = normalize(status);
    }

    @JsonCreator
    public static AigcBrandProfileUpdateDTO fromJson(
            @JsonProperty("name") JsonNode name,
            @JsonProperty("kind") JsonNode kind,
            @JsonProperty("industry") JsonNode industry,
            @JsonProperty("currentVersionId") JsonNode currentVersionId,
            @JsonProperty("status") JsonNode status,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new AigcBrandProfileUpdateDTO(
                Patch.parse(name, AigcBrandPatchDecoder::text),
                Patch.parse(kind, AigcBrandPatchDecoder::text),
                Patch.parse(industry, AigcBrandPatchDecoder::text),
                Patch.parse(currentVersionId, AigcBrandPatchDecoder::longValue),
                Patch.parse(status, AigcBrandPatchDecoder::text),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
