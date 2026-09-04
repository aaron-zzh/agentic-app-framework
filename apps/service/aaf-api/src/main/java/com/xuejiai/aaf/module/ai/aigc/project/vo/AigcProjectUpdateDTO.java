package com.xuejiai.aaf.module.ai.aigc.project.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.definition.Patch;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/** 项目基础展示信息更新请求。 */
public record AigcProjectUpdateDTO(
        Patch<String> name,
        Patch<String> description,
        Patch<String> brief,
        Patch<AigcProjectCoverPatchDTO> cover,
        @NotNull @PositiveOrZero Integer expectedVersion) {

    public AigcProjectUpdateDTO {
        name = normalize(name);
        description = normalize(description);
        brief = normalize(brief);
        cover = normalize(cover);
    }

    @JsonCreator
    public static AigcProjectUpdateDTO fromJson(
            @JsonProperty("name") JsonNode name,
            @JsonProperty("description") JsonNode description,
            @JsonProperty("brief") JsonNode brief,
            @JsonProperty("cover") JsonNode cover,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new AigcProjectUpdateDTO(
                Patch.parse(name, AigcProjectUpdateDTO::decodeName),
                Patch.parse(description, node -> decodeString(node, "项目描述")),
                Patch.parse(brief, node -> decodeString(node, "项目简报")),
                Patch.parse(cover, AigcProjectUpdateDTO::decodeCover),
                expectedVersion);
    }

    private static String decodeName(JsonNode node) {
        var value = decodeString(node, "项目名称");
        if (value.length() > 200) {
            throw badRequest("项目名称长度不能超过 200");
        }
        return value;
    }

    private static String decodeString(JsonNode node, String fieldName) {
        if (!node.isString()) {
            throw badRequest(fieldName + "必须是字符串");
        }
        return node.asString();
    }

    private static BusinessException badRequest(String message) {
        return new BusinessException(GlobalErrorCode.BAD_REQUEST, message);
    }

    private static AigcProjectCoverPatchDTO decodeCover(JsonNode node) {
        if (!node.isObject() || !node.path("operation").isString()) {
            throw new IllegalArgumentException("封面更新格式非法");
        }
        return new AigcProjectCoverPatchDTO(
                AigcProjectCoverOperation.valueOf(node.path("operation").asString()),
                node.path("fileId").isIntegralNumber() ? node.path("fileId").longValue() : null,
                text(node, "prompt"),
                text(node, "idempotencyKey"));
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).isString() ? node.path(field).asString() : null;
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
