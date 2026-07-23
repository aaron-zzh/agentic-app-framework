package com.xuejiai.aaf.framework.crud.reference;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;

/** 所有代码资源引用的默认安全基线。 */
public interface EntityReferenceAccess {

    Set<ResourceReference> readable(Collection<ResourceReference> references);

    Set<ResourceReference> referenceable(Collection<ResourceReference> references);

    Map<ResourceReference, ResourceRefDTO> loadReadable(Collection<ResourceReference> references);

    default boolean canRead(String resource, Long id) {
        var reference = new ResourceReference(resource, id);
        return readable(Set.of(reference)).contains(reference);
    }

    default boolean canReference(String resource, Long id) {
        var reference = new ResourceReference(resource, id);
        return referenceable(Set.of(reference)).contains(reference);
    }

    default void requireReadable(ResourceReference reference, String referenceName) {
        requireValid(reference);
        if (!readable(Set.of(reference)).contains(reference)) {
            throw exception(GlobalErrorCode.CRUD_RESOURCE_NOT_FOUND, referenceName);
        }
    }

    default void requireReferenceable(ResourceReference reference, String referenceName) {
        requireValid(reference);
        if (!referenceable(Set.of(reference)).contains(reference)) {
            throw exception(GlobalErrorCode.CRUD_RESOURCE_NOT_FOUND, referenceName);
        }
    }

    private static void requireValid(ResourceReference reference) {
        if (reference == null
                || reference.resource() == null
                || reference.resource().isBlank()
                || reference.id() == null
                || reference.id() <= 0) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
    }
}
