package com.xuejiai.aaf.module.elder.mall.vo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.framework.crud.definition.Patch;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/** 服务预约更新请求；仅待确认预约可修改业务字段。 */
@Schema(description = "更新待确认服务预约")
public record ServiceOrderUpdateDTO(
        Patch<Long> elderId,
        Patch<Long> serviceItemId,
        Patch<Integer> quantity,
        Patch<LocalDateTime> scheduledAt,
        Patch<String> serviceAddress,
        Patch<String> notes,
        @NotNull @PositiveOrZero @Schema(description = "期望版本号") Integer expectedVersion) {

    public ServiceOrderUpdateDTO {
        elderId = normalize(elderId);
        serviceItemId = normalize(serviceItemId);
        quantity = normalize(quantity);
        scheduledAt = normalize(scheduledAt);
        serviceAddress = normalize(serviceAddress);
        notes = normalize(notes);
    }

    @JsonCreator
    public static ServiceOrderUpdateDTO fromJson(
            @JsonProperty("elderId") JsonNode elderId,
            @JsonProperty("serviceItemId") JsonNode serviceItemId,
            @JsonProperty("quantity") JsonNode quantity,
            @JsonProperty("scheduledAt") JsonNode scheduledAt,
            @JsonProperty("serviceAddress") JsonNode serviceAddress,
            @JsonProperty("notes") JsonNode notes,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new ServiceOrderUpdateDTO(
                Patch.parse(elderId, node -> MallJsonDecoder.longValue(node, "长者 ID")),
                Patch.parse(serviceItemId, node -> MallJsonDecoder.longValue(node, "服务项目 ID")),
                Patch.parse(quantity, node -> MallJsonDecoder.integer(node, "数量")),
                Patch.parse(scheduledAt, node -> MallJsonDecoder.dateTime(node, "预约服务时间")),
                Patch.parse(serviceAddress, node -> MallJsonDecoder.text(node, "服务地址")),
                Patch.parse(notes, node -> MallJsonDecoder.text(node, "预约备注")),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
