package com.xuejiai.aaf.module.content.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.framework.crud.definition.Patch;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/**
 * 项目资料引用更新请求。
 *
 * @author AaronZZH & Kiro
 */
public record ContentProjectProfileRefUpdateDTO(
        Patch<Long> projectId,
        Patch<Long> brandProfileId,
        Patch<String> refScope,
        Patch<String> profileVersion,
        Patch<String> scopeNote,
        @NotNull @PositiveOrZero Integer expectedVersion) {

    public ContentProjectProfileRefUpdateDTO {
        projectId = normalize(projectId);
        brandProfileId = normalize(brandProfileId);
        refScope = normalize(refScope);
        profileVersion = normalize(profileVersion);
        scopeNote = normalize(scopeNote);
    }

    @JsonCreator
    public static ContentProjectProfileRefUpdateDTO fromJson(
            @JsonProperty("projectId") JsonNode projectId,
            @JsonProperty("brandProfileId") JsonNode brandProfileId,
            @JsonProperty("refScope") JsonNode refScope,
            @JsonProperty("profileVersion") JsonNode profileVersion,
            @JsonProperty("scopeNote") JsonNode scopeNote,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new ContentProjectProfileRefUpdateDTO(
                Patch.parse(projectId, ContentPatchDecoder::longValue),
                Patch.parse(brandProfileId, ContentPatchDecoder::longValue),
                Patch.parse(refScope, ContentPatchDecoder::text),
                Patch.parse(profileVersion, ContentPatchDecoder::text),
                Patch.parse(scopeNote, ContentPatchDecoder::text),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
