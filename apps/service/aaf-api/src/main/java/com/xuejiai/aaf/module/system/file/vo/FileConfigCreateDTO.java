package com.xuejiai.aaf.module.system.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 创建文件存储配置请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建文件存储配置")
public record FileConfigCreateDTO(
        @NotBlank(message = "配置名称不能为空") @Schema(description = "配置名称") String name,
        @NotBlank(message = "存储类型不能为空") @Schema(description = "存储类型：LOCAL/S3/OSS")
                String storageType,
        @Schema(description = "配置内容（JSON）") String config) {}
