package com.xuejiai.aaf.module.ai.aigc.configuration.api;

import java.util.List;

/** 交付包允许的槽位、扩展对象与完成策略。 */
public record AigcDeliverableSetSpec(
        String setTemplateKey,
        List<String> allowedSlotTemplateKeys,
        List<String> allowedCustomObjectTypes,
        String defaultUserAddedContractRole,
        String completionMode,
        String reviewMode,
        String publicationPolicy) {

    public AigcDeliverableSetSpec {
        publicationPolicy =
                publicationPolicy == null || publicationPolicy.isBlank()
                        ? "OPTIONAL"
                        : publicationPolicy.toUpperCase();
        if (!java.util.Set.of(
                        "NONE", "OPTIONAL", "AT_LEAST_ONE_SUCCESS", "ALL_SELECTED_CHANNELS")
                .contains(publicationPolicy)) {
            throw new IllegalArgumentException("不支持的 publicationPolicy: " + publicationPolicy);
        }
        allowedSlotTemplateKeys =
                allowedSlotTemplateKeys == null ? List.of() : List.copyOf(allowedSlotTemplateKeys);
        allowedCustomObjectTypes =
                allowedCustomObjectTypes == null ? List.of() : List.copyOf(allowedCustomObjectTypes);
    }
}
