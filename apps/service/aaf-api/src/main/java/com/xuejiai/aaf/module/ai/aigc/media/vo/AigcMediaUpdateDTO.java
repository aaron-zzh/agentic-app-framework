package com.xuejiai.aaf.module.ai.aigc.media.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** AIGC 媒体更新请求。 */
public record AigcMediaUpdateDTO(@NotBlank @Size(max = 200) String name) {}
