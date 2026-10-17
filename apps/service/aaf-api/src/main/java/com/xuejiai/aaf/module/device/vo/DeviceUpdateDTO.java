package com.xuejiai.aaf.module.device.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.framework.crud.definition.Patch;
import com.xuejiai.aaf.framework.crud.reference.ResourceReference;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/** 设备可编辑属性。场景主体变更会重新执行场景绑定校验。 */
@Schema(description = "更新设备")
public record DeviceUpdateDTO(
        Patch<String> name,
        Patch<String> deviceType,
        Patch<String> zone,
        Patch<String> scenarioKey,
        Patch<ResourceReference> subject,
        Patch<String> roleKey,
        @NotNull @PositiveOrZero @Schema(description = "期望版本号") Integer expectedVersion) {

    public DeviceUpdateDTO {
        name = normalize(name);
        deviceType = normalize(deviceType);
        zone = normalize(zone);
        scenarioKey = normalize(scenarioKey);
        subject = normalize(subject);
        roleKey = normalize(roleKey);
    }

    @JsonCreator
    public static DeviceUpdateDTO fromJson(
            @JsonProperty("name") JsonNode name,
            @JsonProperty("deviceType") JsonNode deviceType,
            @JsonProperty("zone") JsonNode zone,
            @JsonProperty("scenarioKey") JsonNode scenarioKey,
            @JsonProperty("subject") JsonNode subject,
            @JsonProperty("roleKey") JsonNode roleKey,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new DeviceUpdateDTO(
                Patch.parse(name, node -> DeviceJsonDecoder.text(node, "设备名称")),
                Patch.parse(deviceType, node -> DeviceJsonDecoder.text(node, "设备类型")),
                Patch.parse(zone, node -> DeviceJsonDecoder.text(node, "设备区域")),
                Patch.parse(scenarioKey, node -> DeviceJsonDecoder.text(node, "设备场景")),
                Patch.parse(subject, DeviceJsonDecoder::reference),
                Patch.parse(roleKey, node -> DeviceJsonDecoder.text(node, "角色覆盖")),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
