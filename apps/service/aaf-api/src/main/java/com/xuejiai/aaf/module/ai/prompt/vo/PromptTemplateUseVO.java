package com.xuejiai.aaf.module.ai.prompt.vo;

/** 提示词模板服务端编译结果。 */
public record PromptTemplateUseVO(
        Long id, String prompt, String negativePrompt, Integer usageCount) {}
