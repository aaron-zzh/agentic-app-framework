package com.xuejiai.aaf.framework.crud.reference;

import java.util.Objects;

/** 批量引用授权中的源记录与目标引用。 */
public record ReferenceRequest(Long sourceId, ResourceReference target) {

    public ReferenceRequest {
        target = Objects.requireNonNull(target, "target");
    }
}
