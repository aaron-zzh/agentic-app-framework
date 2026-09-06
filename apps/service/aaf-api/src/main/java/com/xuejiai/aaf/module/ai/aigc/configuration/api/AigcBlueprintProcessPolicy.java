package com.xuejiai.aaf.module.ai.aigc.configuration.api;

import java.util.List;

/** 蓝图允许的审核、发布、自动动作与确认门策略。 */
public record AigcBlueprintProcessPolicy(
        boolean reviewRequired,
        String publicationPolicy,
        List<String> autoActionKeys,
        List<String> confirmationGateKeys) {

    public AigcBlueprintProcessPolicy {
        publicationPolicy =
                publicationPolicy == null || publicationPolicy.isBlank()
                        ? "OPTIONAL"
                        : publicationPolicy.toUpperCase();
        if (!java.util.Set.of("NONE", "OPTIONAL", "AT_LEAST_ONE_SUCCESS", "ALL_SELECTED_CHANNELS")
                .contains(publicationPolicy)) {
            throw new IllegalArgumentException("不支持的 publicationPolicy: " + publicationPolicy);
        }
        autoActionKeys = autoActionKeys == null ? List.of() : List.copyOf(autoActionKeys);
        confirmationGateKeys =
                confirmationGateKeys == null ? List.of() : List.copyOf(confirmationGateKeys);
    }
}
