package com.xuejiai.aaf.autodev.doc.vo;

import jakarta.validation.constraints.NotBlank;

/** 新建开发文档请求。 */
public record AutodevDocCreateDTO(
        @NotBlank String title, @NotBlank String filePath, String docType, String content) {}
