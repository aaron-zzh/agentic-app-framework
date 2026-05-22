package com.xuejiai.aaf.module.document.vo;

import jakarta.validation.constraints.NotBlank;

/** 新建文档请求。 */
public record DocCreateDTO(
        @NotBlank String title,
        @NotBlank String filePath,
        String docType,
        String content) {}
