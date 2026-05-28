package com.xuejiai.aaf.module.system.file.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文件存储配置响应。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "文件存储配置")
public record FileConfigVO(
        @Schema(description = "ID") Long id,
        @Schema(description = "配置名称") String name,
        @Schema(description = "存储类型：LOCAL/S3/OSS") String storageType,
        @Schema(description = "配置内容（JSON）") String config,
        @Schema(description = "是否主配置") Boolean master,
        @Schema(description = "状态（0 正常 / 1 禁用）") Integer status,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
