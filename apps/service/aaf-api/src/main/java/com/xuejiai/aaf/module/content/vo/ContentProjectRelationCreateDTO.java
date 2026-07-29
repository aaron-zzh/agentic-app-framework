package com.xuejiai.aaf.module.content.vo;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 项目关系创建请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建项目关系")
public record ContentProjectRelationCreateDTO(
        @NotNull Long projectId,
        @NotBlank String relationType,
        @NotBlank String layer,
        @NotNull Long sourceObjectId,
        @NotNull Long targetObjectId,
        Map<String, Object> relationMeta) {}
