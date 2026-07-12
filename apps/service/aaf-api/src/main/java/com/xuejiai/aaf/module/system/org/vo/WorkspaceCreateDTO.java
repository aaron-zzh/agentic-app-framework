package com.xuejiai.aaf.module.system.org.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 创建工作区请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建工作区")
public record WorkspaceCreateDTO(
        @NotBlank @Schema(description = "工作区名称") String name,
        @NotBlank
                @Pattern(regexp = "^[a-z0-9-]+$", message = "标识只能包含小写字母、数字和短横线")
                @Schema(description = "工作区标识（组织内唯一，URL 友好）")
                String slug) {}
