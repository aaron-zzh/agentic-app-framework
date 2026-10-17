package com.xuejiai.aaf.module.elder.profile.vo;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.framework.crud.definition.Patch;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/** 长者健康条目更新请求。 */
@Schema(description = "更新长者健康条目")
public record ElderHealthItemUpdateDTO(
        Patch<Long> elderId,
        Patch<String> itemType,
        Patch<String> name,
        Patch<String> description,
        Patch<LocalDate> occurredDate,
        @NotNull @PositiveOrZero @Schema(description = "期望版本号") Integer expectedVersion) {

    public ElderHealthItemUpdateDTO {
        elderId = normalize(elderId);
        itemType = normalize(itemType);
        name = normalize(name);
        description = normalize(description);
        occurredDate = normalize(occurredDate);
    }

    @JsonCreator
    public static ElderHealthItemUpdateDTO fromJson(
            @JsonProperty("elderId") JsonNode elderId,
            @JsonProperty("itemType") JsonNode itemType,
            @JsonProperty("name") JsonNode name,
            @JsonProperty("description") JsonNode description,
            @JsonProperty("occurredDate") JsonNode occurredDate,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new ElderHealthItemUpdateDTO(
                Patch.parse(elderId, node -> ProfileJsonDecoder.longValue(node, "长者 ID")),
                Patch.parse(itemType, node -> ProfileJsonDecoder.text(node, "健康条目类型")),
                Patch.parse(name, node -> ProfileJsonDecoder.text(node, "条目名称")),
                Patch.parse(description, node -> ProfileJsonDecoder.text(node, "详细说明")),
                Patch.parse(occurredDate, node -> ProfileJsonDecoder.date(node, "发生日期")),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
