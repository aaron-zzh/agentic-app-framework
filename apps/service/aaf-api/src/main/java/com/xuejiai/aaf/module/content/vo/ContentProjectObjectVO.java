package com.xuejiai.aaf.module.content.vo;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 项目对象响应。
 *
 * @author AaronZZH & Kiro
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "项目对象信息")
public record ContentProjectObjectVO(
        Long id,
        Integer version,
        Long projectId,
        String objectType,
        String objectKey,
        String blueprintNodeKey,
        Long parentId,
        Integer sortOrder,
        String title,
        String status,
        String source,
        String schemaVersion,
        String entityResource,
        Long entityId,
        String adoptedVersionRef,
        String summary,
        Map<String, Object> payload) {}
