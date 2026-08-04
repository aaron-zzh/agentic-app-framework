package com.xuejiai.aaf.module.ai.aigc.media.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Media 更新请求。 */
public record MediaUpdateDTO(
        @NotBlank @Size(max = 200) String name) {}
