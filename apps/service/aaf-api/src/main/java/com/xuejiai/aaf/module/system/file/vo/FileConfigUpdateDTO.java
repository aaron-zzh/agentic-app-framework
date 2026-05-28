package com.xuejiai.aaf.module.system.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 更新文件存储配置请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "更新文件存储配置")
public record FileConfigUpdateDTO(
        @Schema(description = "配置名称") String name,
        @Schema(description = "存储类型：LOCAL/S3/OSS") String storageType,
        @Schema(description = "配置内容（JSON）") String config,
        @Schema(description = "状态（0 正常 / 1 禁用）") Integer status) {}
