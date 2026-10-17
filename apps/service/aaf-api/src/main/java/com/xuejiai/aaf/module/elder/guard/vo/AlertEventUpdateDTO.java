package com.xuejiai.aaf.module.elder.guard.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.framework.crud.definition.Patch;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/** 预警结论更新请求；只允许将已知晓预警结案为已处理或误报。 */
@Schema(description = "预警结论更新请求")
public record AlertEventUpdateDTO(
        Patch<String> status,
        Patch<String> remark,
        @NotNull @PositiveOrZero @Schema(description = "期望版本号") Integer expectedVersion) {

    public AlertEventUpdateDTO {
        status = normalize(status);
        remark = normalize(remark);
    }

    @JsonCreator
    public static AlertEventUpdateDTO fromJson(
            @JsonProperty("status") JsonNode status,
            @JsonProperty("remark") JsonNode remark,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new AlertEventUpdateDTO(
                Patch.parse(status, node -> GuardJsonDecoder.text(node, "预警状态")),
                Patch.parse(remark, node -> GuardJsonDecoder.text(node, "处理备注")),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
