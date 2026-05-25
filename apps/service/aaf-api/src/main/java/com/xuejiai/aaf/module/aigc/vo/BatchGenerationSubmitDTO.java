package com.xuejiai.aaf.module.aigc.vo;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/** 批量生成提交请求。 */
public record BatchGenerationSubmitDTO(
        @NotEmpty List<@NotBlank String> prompts,
        String model,
        Integer width,
        Integer height) {}
