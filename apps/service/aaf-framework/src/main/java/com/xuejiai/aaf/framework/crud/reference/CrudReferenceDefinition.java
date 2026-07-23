package com.xuejiai.aaf.framework.crud.reference;

import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.crud.definition.ResourceKey;

/** 编译后的固定或多态资源引用契约。 */
public record CrudReferenceDefinition(
        String key,
        String idProperty,
        ResourceKey targetResource,
        String resourceProperty,
        String inputField,
        String viewField,
        String additionalPolicyBean,
        Set<ReferenceCapability> capabilities) {

    public CrudReferenceDefinition {
        key = requireText(key, "key");
        idProperty = requireText(idProperty, "idProperty");
        resourceProperty = optionalText(resourceProperty);
        inputField = optionalText(inputField);
        viewField = optionalText(viewField);
        additionalPolicyBean = optionalText(additionalPolicyBean);
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
        if (capabilities.isEmpty()) {
            throw new IllegalArgumentException("引用能力不能为空");
        }
        if ((targetResource == null) == resourceProperty.isBlank()) {
            throw new IllegalArgumentException("固定目标资源与多态资源属性必须且只能声明一个");
        }
    }

    public boolean supports(ReferenceCapability capability) {
        return capabilities.contains(capability);
    }

    public boolean polymorphic() {
        return targetResource == null;
    }

    private static String requireText(String value, String name) {
        var normalized = optionalText(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return normalized;
    }

    private static String optionalText(String value) {
        return value == null ? "" : value.trim();
    }
}
