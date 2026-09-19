package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.definition.Patch;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcConfigurationPatchDecoder;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/** 项目基础展示信息更新请求。 */
public record AigcProjectUpdateDTO(
        Patch<String> name,
        Patch<String> description,
        Patch<String> background,
        Patch<String> targetAudience,
        Patch<List<String>> requirements,
        Patch<List<String>> constraints,
        Patch<List<String>> keyFacts,
        Patch<List<String>> keyMessages,
        Patch<String> styleGuidance,
        Patch<List<String>> successCriteria,
        Patch<Map<String, Object>> deliveryRequirements,
        Patch<String> locale,
        Patch<String> additionalContext,
        Patch<AigcProjectCoverPatchDTO> cover,
        @NotNull @PositiveOrZero Integer expectedVersion) {

    public AigcProjectUpdateDTO {
        name = normalize(name);
        description = normalize(description);
        background = normalize(background);
        targetAudience = normalize(targetAudience);
        requirements = normalize(requirements);
        constraints = normalize(constraints);
        keyFacts = normalize(keyFacts);
        keyMessages = normalize(keyMessages);
        styleGuidance = normalize(styleGuidance);
        successCriteria = normalize(successCriteria);
        deliveryRequirements = normalize(deliveryRequirements);
        locale = normalize(locale);
        additionalContext = normalize(additionalContext);
        cover = normalize(cover);
    }

    @JsonCreator
    public static AigcProjectUpdateDTO fromJson(
            @JsonProperty("name") JsonNode name,
            @JsonProperty("description") JsonNode description,
            @JsonProperty("background") JsonNode background,
            @JsonProperty("targetAudience") JsonNode targetAudience,
            @JsonProperty("requirements") JsonNode requirements,
            @JsonProperty("constraints") JsonNode constraints,
            @JsonProperty("keyFacts") JsonNode keyFacts,
            @JsonProperty("keyMessages") JsonNode keyMessages,
            @JsonProperty("styleGuidance") JsonNode styleGuidance,
            @JsonProperty("successCriteria") JsonNode successCriteria,
            @JsonProperty("deliveryRequirements") JsonNode deliveryRequirements,
            @JsonProperty("locale") JsonNode locale,
            @JsonProperty("additionalContext") JsonNode additionalContext,
            @JsonProperty("cover") JsonNode cover,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new AigcProjectUpdateDTO(
                Patch.parse(name, AigcProjectUpdateDTO::decodeName),
                Patch.parse(description, node -> decodeString(node, "项目描述")),
                Patch.parse(background, node -> decodeString(node, "项目背景")),
                Patch.parse(targetAudience, node -> decodeString(node, "目标受众")),
                Patch.parse(requirements, AigcConfigurationPatchDecoder::stringList),
                Patch.parse(constraints, AigcConfigurationPatchDecoder::stringList),
                Patch.parse(keyFacts, AigcConfigurationPatchDecoder::stringList),
                Patch.parse(keyMessages, AigcConfigurationPatchDecoder::stringList),
                Patch.parse(styleGuidance, node -> decodeString(node, "风格要求")),
                Patch.parse(successCriteria, AigcConfigurationPatchDecoder::stringList),
                Patch.parse(deliveryRequirements, AigcConfigurationPatchDecoder::objectMap),
                Patch.parse(locale, node -> decodeString(node, "语言地区")),
                Patch.parse(additionalContext, node -> decodeString(node, "补充上下文")),
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
