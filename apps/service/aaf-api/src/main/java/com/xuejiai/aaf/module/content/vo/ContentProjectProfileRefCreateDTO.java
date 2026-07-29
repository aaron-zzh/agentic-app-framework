package com.xuejiai.aaf.module.content.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 项目资料引用创建请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建项目资料引用")
public record ContentProjectProfileRefCreateDTO(
        @NotNull Long projectId,
        @NotNull Long brandProfileId,
        @NotBlank String refScope,
        String profileVersion,
        String scopeNote) {}
