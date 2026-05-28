package com.xuejiai.aaf.module.system.file.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文件记录响应。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "文件记录")
public record FileRecordVO(
        @Schema(description = "ID") Long id,
        @Schema(description = "文件 key") String key,
        @Schema(description = "原始文件名") String originalName,
        @Schema(description = "MIME 类型") String mimeType,
        @Schema(description = "文件大小（字节）") Long size,
        @Schema(description = "上传者 ID") Long uploaderId,
        @Schema(description = "上传时间") LocalDateTime createTime) {}
