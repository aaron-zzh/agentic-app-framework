package com.xuejiai.aaf.module.document.vo;

import jakarta.validation.constraints.NotBlank;

/** 更新文档请求。 */
public record DocUpdateDTO(@NotBlank String content) {}
