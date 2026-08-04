package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.framework.crud.definition.Patch;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/** 更新项目类型兼容包草稿。 */
public record AigcProjectTypePackageUpdateDTO(
        Patch<String> packageVersion,
        Patch<Long> projectTypeId,
        Patch<Long> blueprintId,
        Patch<Long> domainExtensionId,
        Patch<List<Long>> channelSpecIds,
        Patch<List<Long>> executionBindingIds,
        Patch<String> productionMode,
        @NotNull @PositiveOrZero Integer expectedVersion) {

    public AigcProjectTypePackageUpdateDTO {
        packageVersion = normalize(packageVersion);
        projectTypeId = normalize(projectTypeId);
        blueprintId = normalize(blueprintId);
        domainExtensionId = normalize(domainExtensionId);
        channelSpecIds = normalize(channelSpecIds);
        executionBindingIds = normalize(executionBindingIds);
        productionMode = normalize(productionMode);
    }

    @JsonCreator
    public static AigcProjectTypePackageUpdateDTO fromJson(
            @JsonProperty("packageVersion") JsonNode packageVersion,
            @JsonProperty("projectTypeId") JsonNode projectTypeId,
            @JsonProperty("blueprintId") JsonNode blueprintId,
            @JsonProperty("domainExtensionId") JsonNode domainExtensionId,
            @JsonProperty("channelSpecIds") JsonNode channelSpecIds,
            @JsonProperty("executionBindingIds") JsonNode executionBindingIds,
            @JsonProperty("productionMode") JsonNode productionMode,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new AigcProjectTypePackageUpdateDTO(
                Patch.parse(packageVersion, AigcConfigurationPatchDecoder::text),
                Patch.parse(projectTypeId, AigcConfigurationPatchDecoder::longValue),
                Patch.parse(blueprintId, AigcConfigurationPatchDecoder::longValue),
                Patch.parse(domainExtensionId, AigcConfigurationPatchDecoder::longValue),
                Patch.parse(channelSpecIds, AigcConfigurationPatchDecoder::longList),
                Patch.parse(executionBindingIds, AigcConfigurationPatchDecoder::longList),
                Patch.parse(productionMode, AigcConfigurationPatchDecoder::text),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
