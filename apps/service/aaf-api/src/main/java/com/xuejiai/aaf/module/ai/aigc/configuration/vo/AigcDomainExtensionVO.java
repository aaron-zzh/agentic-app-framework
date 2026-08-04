package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/** 领域扩展响应。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "AIGC 领域扩展信息")
public record AigcDomainExtensionVO(
        Long id,
        Integer version,
        String code,
        String name,
        String extensionVersion,
        String industry,
        String region,
        String language,
        String status,
        Map<String, Object> profileSchemaExt,
        Map<String, Object> objectDefinitions,
        Map<String, Object> knowledgeRequirements,
        Map<String, Object> ruleSets,
        List<String> validators,
        List<String> roleRecommendations,
        Map<String, Object> actionConstraints,
        Map<String, Object> channelOverrides,
        Map<String, Object> migrationDeclaration) {}
