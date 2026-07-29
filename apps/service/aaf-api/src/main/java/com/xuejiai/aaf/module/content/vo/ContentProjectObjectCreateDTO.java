package com.xuejiai.aaf.module.content.vo;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 项目对象创建请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建项目对象")
public record ContentProjectObjectCreateDTO(
        @NotNull Long projectId,
        @NotBlank String objectType,
        @NotBlank String objectKey,
        String blueprintNodeKey,
        Long parentId,
        @NotNull Integer sortOrder,
        String title,
        @NotBlank String status,
        @NotBlank String source,
        String schemaVersion,
        String entityResource,
        Long entityId,
        String adoptedVersionRef,
        String summary,
        Map<String, Object> payload) {}
