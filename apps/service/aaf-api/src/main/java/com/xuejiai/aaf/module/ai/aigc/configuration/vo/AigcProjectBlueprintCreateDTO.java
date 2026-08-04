package com.xuejiai.aaf.module.ai.aigc.configuration.vo;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 项目蓝图创建请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建项目蓝图")
public record AigcProjectBlueprintCreateDTO(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String projectTypeCode,
        @NotBlank String blueprintVersion,
        @NotBlank String productionMode,
        String description,
        Map<String, Object> objectSpec,
        Map<String, Object> relationSpec,
        Map<String, Object> deliverableSpec,
        List<String> actionKeys,
        List<String> confirmationGates,
        List<String> briefFields) {}
