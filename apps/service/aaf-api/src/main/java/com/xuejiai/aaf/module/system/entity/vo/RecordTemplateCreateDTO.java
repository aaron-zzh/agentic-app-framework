package com.xuejiai.aaf.module.system.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建记录模板请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建记录模板")
public record RecordTemplateCreateDTO(
        @NotBlank @Size(max = 100) String entitySlug,
        @NotBlank @Size(max = 200) String name,
        @NotBlank String fieldValues,
        Boolean isShared,
        Boolean isDefault) {}
