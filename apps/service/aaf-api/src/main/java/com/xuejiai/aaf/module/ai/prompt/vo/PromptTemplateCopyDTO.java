package com.xuejiai.aaf.module.ai.prompt.vo;

import jakarta.validation.constraints.Size;

/** 复制提示词模板请求；名称为空时自动追加“副本”。 */
public record PromptTemplateCopyDTO(@Size(max = 128) String name) {}
