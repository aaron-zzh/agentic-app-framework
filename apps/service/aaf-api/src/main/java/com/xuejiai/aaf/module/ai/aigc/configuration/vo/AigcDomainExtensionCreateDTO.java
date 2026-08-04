package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 行业扩展创建请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建行业扩展")
public record AigcDomainExtensionCreateDTO(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String extensionVersion,
        String industry,
        String region,
        String language,
        Map<String, Object> profileSchemaExt,
        Map<String, Object> objectDefinitions,
        Map<String, Object> knowledgeRequirements,
        Map<String, Object> ruleSets,
        List<String> validators,
        List<String> roleRecommendations,
        Map<String, Object> actionConstraints,
        Map<String, Object> channelOverrides,
        Map<String, Object> migrationDeclaration) {}
