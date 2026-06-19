package com.xuejiai.aaf.module.system.file.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** 前端直传完成后确认文件的请求体（预签名/STS分片上传场景）。 */
public record FileConfirmDTO(
        @NotBlank String key,
        @NotBlank String originalName,
        String mimeType,
        @Positive long size) {}
