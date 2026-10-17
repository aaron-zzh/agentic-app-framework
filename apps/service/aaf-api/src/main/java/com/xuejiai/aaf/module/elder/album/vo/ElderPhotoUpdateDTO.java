package com.xuejiai.aaf.module.elder.album.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.framework.crud.definition.Patch;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/** 长者照片更新请求；一期仅允许维护说明。 */
@Schema(description = "更新长者照片说明")
public record ElderPhotoUpdateDTO(
        Patch<String> description,
        @NotNull @PositiveOrZero @Schema(description = "期望版本号") Integer expectedVersion) {

    public ElderPhotoUpdateDTO {
        description = description == null ? Patch.absent() : description;
    }

    @JsonCreator
    public static ElderPhotoUpdateDTO fromJson(
            @JsonProperty("description") JsonNode description,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new ElderPhotoUpdateDTO(
                Patch.parse(description, ElderPhotoUpdateDTO::textValue), expectedVersion);
    }

    private static String textValue(JsonNode node) {
        if (!node.isString()) {
            throw new IllegalArgumentException("照片说明必须是字符串");
        }
        return node.asString();
    }
}
