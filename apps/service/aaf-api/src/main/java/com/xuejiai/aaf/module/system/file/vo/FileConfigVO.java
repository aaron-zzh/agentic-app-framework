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
        @Schema(description = "版本号") Integer version,
        @Schema(description = "配置名称") String name,
        @Schema(description = "存储类型：LOCAL/S3/OSS") String storageType,
        @Schema(description = "脱敏后的配置内容（JSON）") String config,
        @Schema(description = "凭证引用；本地存储为空") String credentialRef,
        @Schema(description = "凭证引用是否可由当前运行环境解析") boolean credentialConfigured,
        @Schema(description = "是否主配置") Boolean master,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
