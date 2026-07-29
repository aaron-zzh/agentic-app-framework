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
 * 执行记录更新请求。
 *
 * @author AaronZZH & Kiro
 */
public record ContentExecutionRunUpdateDTO(
        Patch<Long> projectId,
        Patch<Long> objectId,
        Patch<String> actionKey,
        Patch<String> targetType,
        Patch<String> targetRef,
        Patch<String> status,
        Patch<String> generationMode,
        Patch<String> roleProfileCode,
        Patch<String> modelPolicyVersion,
        Patch<String> selectedModelVersion,
        Patch<String> skillDefinitionVersionId,
        Patch<String> promptText,
        Patch<List<String>> snippetRefs,
        Patch<List<String>> attachmentRefs,
        Patch<List<Map<String, Object>>> toolCalls,
        Patch<Map<String, Object>> inputPayload,
        Patch<Map<String, Object>> outputPayload,
        Patch<BigDecimal> costCredits,
        Patch<String> errorMessage,
        Patch<LocalDateTime> startTime,
        Patch<LocalDateTime> endTime,
        Patch<Long> aigcTaskId,
        @NotNull @PositiveOrZero Integer expectedVersion) {

    public ContentExecutionRunUpdateDTO {
        projectId = normalize(projectId);
        objectId = normalize(objectId);
        actionKey = normalize(actionKey);
        targetType = normalize(targetType);
        targetRef = normalize(targetRef);
        status = normalize(status);
        generationMode = normalize(generationMode);
        roleProfileCode = normalize(roleProfileCode);
        modelPolicyVersion = normalize(modelPolicyVersion);
        selectedModelVersion = normalize(selectedModelVersion);
        skillDefinitionVersionId = normalize(skillDefinitionVersionId);
        promptText = normalize(promptText);
        snippetRefs = normalize(snippetRefs);
        attachmentRefs = normalize(attachmentRefs);
        toolCalls = normalize(toolCalls);
        inputPayload = normalize(inputPayload);
        outputPayload = normalize(outputPayload);
        costCredits = normalize(costCredits);
        errorMessage = normalize(errorMessage);
        startTime = normalize(startTime);
        endTime = normalize(endTime);
        aigcTaskId = normalize(aigcTaskId);
    }

    @JsonCreator
    public static ContentExecutionRunUpdateDTO fromJson(
            @JsonProperty("projectId") JsonNode projectId,
            @JsonProperty("objectId") JsonNode objectId,
            @JsonProperty("actionKey") JsonNode actionKey,
            @JsonProperty("targetType") JsonNode targetType,
            @JsonProperty("targetRef") JsonNode targetRef,
            @JsonProperty("status") JsonNode status,
            @JsonProperty("generationMode") JsonNode generationMode,
            @JsonProperty("roleProfileCode") JsonNode roleProfileCode,
            @JsonProperty("modelPolicyVersion") JsonNode modelPolicyVersion,
            @JsonProperty("selectedModelVersion") JsonNode selectedModelVersion,
            @JsonProperty("skillDefinitionVersionId") JsonNode skillDefinitionVersionId,
            @JsonProperty("promptText") JsonNode promptText,
            @JsonProperty("snippetRefs") JsonNode snippetRefs,
            @JsonProperty("attachmentRefs") JsonNode attachmentRefs,
            @JsonProperty("toolCalls") JsonNode toolCalls,
            @JsonProperty("inputPayload") JsonNode inputPayload,
            @JsonProperty("outputPayload") JsonNode outputPayload,
            @JsonProperty("costCredits") JsonNode costCredits,
            @JsonProperty("errorMessage") JsonNode errorMessage,
            @JsonProperty("startTime") JsonNode startTime,
            @JsonProperty("endTime") JsonNode endTime,
            @JsonProperty("aigcTaskId") JsonNode aigcTaskId,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new ContentExecutionRunUpdateDTO(
                Patch.parse(projectId, ContentPatchDecoder::longValue),
                Patch.parse(objectId, ContentPatchDecoder::longValue),
                Patch.parse(actionKey, ContentPatchDecoder::text),
                Patch.parse(targetType, ContentPatchDecoder::text),
                Patch.parse(targetRef, ContentPatchDecoder::text),
                Patch.parse(status, ContentPatchDecoder::text),
                Patch.parse(generationMode, ContentPatchDecoder::text),
                Patch.parse(roleProfileCode, ContentPatchDecoder::text),
                Patch.parse(modelPolicyVersion, ContentPatchDecoder::text),
                Patch.parse(selectedModelVersion, ContentPatchDecoder::text),
                Patch.parse(skillDefinitionVersionId, ContentPatchDecoder::text),
                Patch.parse(promptText, ContentPatchDecoder::text),
                Patch.parse(snippetRefs, ContentPatchDecoder::stringList),
                Patch.parse(attachmentRefs, ContentPatchDecoder::stringList),
                Patch.parse(toolCalls, ContentPatchDecoder::objectList),
                Patch.parse(inputPayload, ContentPatchDecoder::objectMap),
                Patch.parse(outputPayload, ContentPatchDecoder::objectMap),
                Patch.parse(costCredits, ContentPatchDecoder::decimal),
                Patch.parse(errorMessage, ContentPatchDecoder::text),
                Patch.parse(startTime, ContentPatchDecoder::dateTime),
                Patch.parse(endTime, ContentPatchDecoder::dateTime),
                Patch.parse(aigcTaskId, ContentPatchDecoder::longValue),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
