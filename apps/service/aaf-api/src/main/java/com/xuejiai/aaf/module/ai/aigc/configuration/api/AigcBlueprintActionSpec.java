package com.xuejiai.aaf.module.ai.aigc.configuration.api;

import java.util.List;

/** 蓝图动作与槽位模板的强类型关联。 */
public record AigcBlueprintActionSpec(
        String actionKey,
        String targetTemplateKey,
        List<String> dependencyTemplateKeys,
        List<String> requiredContextKeys,
        String confirmationPolicy,
        boolean isDefault,
        String inputPresetJson) {

    public AigcBlueprintActionSpec {
        dependencyTemplateKeys =
                dependencyTemplateKeys == null ? List.of() : List.copyOf(dependencyTemplateKeys);
        requiredContextKeys =
                requiredContextKeys == null ? List.of() : List.copyOf(requiredContextKeys);
    }
}
