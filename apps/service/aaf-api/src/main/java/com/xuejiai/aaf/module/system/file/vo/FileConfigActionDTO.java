package com.xuejiai.aaf.module.system.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 文件存储配置实体动作请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "文件存储配置实体动作请求")
public record FileConfigActionDTO(
        @NotNull(message = "配置 ID 不能为空") @Schema(description = "文件存储配置 ID") Long id) {}
