package com.xuejiai.aaf.framework.crud.reference;

import java.util.Objects;

import com.xuejiai.aaf.framework.crud.definition.ResourceKey;

/** 单次资源引用授权所需的源、目标和主体上下文。 */
public record ReferenceContext(
        ResourceKey sourceResource,
        Long sourceId,
        String relationKey,
        ResourceReference target,
        Long subjectId,
        Long orgId,
        Long workspaceId) {

    public ReferenceContext {
        sourceResource = Objects.requireNonNull(sourceResource, "sourceResource");
        relationKey = requireText(relationKey, "relationKey");
        target = Objects.requireNonNull(target, "target");
        if (target.resource() == null
                || target.resource().isBlank()
                || target.id() == null
                || target.id() <= 0) {
            throw new IllegalArgumentException("目标资源引用非法");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value.trim();
    }
}
