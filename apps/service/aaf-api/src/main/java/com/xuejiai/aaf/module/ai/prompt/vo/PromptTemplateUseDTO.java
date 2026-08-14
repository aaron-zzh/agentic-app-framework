package com.xuejiai.aaf.module.ai.prompt.vo;

import java.util.Map;

import jakarta.validation.constraints.Size;

/** 使用提示词模板时提交的变量。 */
public record PromptTemplateUseDTO(
        @Size(max = 50) Map<String, @Size(max = 20_000) String> variables) {}
