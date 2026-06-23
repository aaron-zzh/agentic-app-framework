package com.xuejiai.aaf.module.ai.aigc.template.vo;

/** Fork 模板创建项目参数。 */
public record UserProjectTemplateForkDTO(
        String name, // 可选，默认=模板名
        String description) {}
