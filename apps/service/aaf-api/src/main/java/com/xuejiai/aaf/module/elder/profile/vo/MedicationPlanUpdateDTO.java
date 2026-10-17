package com.xuejiai.aaf.module.elder.profile.vo;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.framework.crud.definition.Patch;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/** 用药计划更新请求。 */
@Schema(description = "更新用药计划")
public record MedicationPlanUpdateDTO(
        Patch<Long> elderId,
        Patch<String> medicationName,
        Patch<String> dosageInstructions,
        Patch<List<String>> reminderTimes,
        Patch<LocalDate> startDate,
        Patch<LocalDate> endDate,
        Patch<String> status,
        @NotNull @PositiveOrZero @Schema(description = "期望版本号") Integer expectedVersion) {

    public MedicationPlanUpdateDTO {
        elderId = normalize(elderId);
        medicationName = normalize(medicationName);
        dosageInstructions = normalize(dosageInstructions);
        reminderTimes = normalize(reminderTimes);
        startDate = normalize(startDate);
        endDate = normalize(endDate);
        status = normalize(status);
    }

    @JsonCreator
    public static MedicationPlanUpdateDTO fromJson(
            @JsonProperty("elderId") JsonNode elderId,
            @JsonProperty("medicationName") JsonNode medicationName,
            @JsonProperty("dosageInstructions") JsonNode dosageInstructions,
            @JsonProperty("reminderTimes") JsonNode reminderTimes,
            @JsonProperty("startDate") JsonNode startDate,
            @JsonProperty("endDate") JsonNode endDate,
            @JsonProperty("status") JsonNode status,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new MedicationPlanUpdateDTO(
                Patch.parse(elderId, node -> ProfileJsonDecoder.longValue(node, "长者 ID")),
                Patch.parse(medicationName, node -> ProfileJsonDecoder.text(node, "药品名称")),
                Patch.parse(dosageInstructions, node -> ProfileJsonDecoder.text(node, "剂量说明")),
                Patch.parse(reminderTimes, node -> ProfileJsonDecoder.textList(node, "提醒时间")),
                Patch.parse(startDate, node -> ProfileJsonDecoder.date(node, "生效日期")),
                Patch.parse(endDate, node -> ProfileJsonDecoder.date(node, "结束日期")),
                Patch.parse(status, node -> ProfileJsonDecoder.text(node, "计划状态")),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
