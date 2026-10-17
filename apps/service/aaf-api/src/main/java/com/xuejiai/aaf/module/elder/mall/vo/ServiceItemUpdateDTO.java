package com.xuejiai.aaf.module.elder.mall.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.framework.crud.definition.Patch;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/** 服务项目更新请求。 */
@Schema(description = "更新服务项目")
public record ServiceItemUpdateDTO(
        Patch<String> name,
        Patch<String> description,
        Patch<String> category,
        Patch<String> unit,
        Patch<String> priceDescription,
        Patch<Integer> durationMinutes,
        Patch<String> status,
        @NotNull @PositiveOrZero @Schema(description = "期望版本号") Integer expectedVersion) {

    public ServiceItemUpdateDTO {
        name = normalize(name);
        description = normalize(description);
        category = normalize(category);
        unit = normalize(unit);
        priceDescription = normalize(priceDescription);
        durationMinutes = normalize(durationMinutes);
        status = normalize(status);
    }

    @JsonCreator
    public static ServiceItemUpdateDTO fromJson(
            @JsonProperty("name") JsonNode name,
            @JsonProperty("description") JsonNode description,
            @JsonProperty("category") JsonNode category,
            @JsonProperty("unit") JsonNode unit,
            @JsonProperty("priceDescription") JsonNode priceDescription,
            @JsonProperty("durationMinutes") JsonNode durationMinutes,
            @JsonProperty("status") JsonNode status,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new ServiceItemUpdateDTO(
                Patch.parse(name, node -> MallJsonDecoder.text(node, "项目名称")),
                Patch.parse(description, node -> MallJsonDecoder.text(node, "项目说明")),
                Patch.parse(category, node -> MallJsonDecoder.text(node, "服务分类")),
                Patch.parse(unit, node -> MallJsonDecoder.text(node, "服务单位")),
                Patch.parse(priceDescription, node -> MallJsonDecoder.text(node, "价格说明")),
                Patch.parse(durationMinutes, node -> MallJsonDecoder.integer(node, "服务时长")),
                Patch.parse(status, node -> MallJsonDecoder.text(node, "项目状态")),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
