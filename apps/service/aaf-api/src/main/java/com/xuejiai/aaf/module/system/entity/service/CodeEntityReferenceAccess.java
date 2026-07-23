package com.xuejiai.aaf.module.system.entity.service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.framework.crud.reference.EntityReferenceAccess;
import com.xuejiai.aaf.framework.crud.reference.ResourceReference;
import com.xuejiai.aaf.framework.crud.runtime.CrudResourceAccessRegistry;

import lombok.RequiredArgsConstructor;

/** 通过运行时 CRUD 访问目录执行默认引用基线。 */
@Component
@RequiredArgsConstructor
public class CodeEntityReferenceAccess implements EntityReferenceAccess {

    private final CrudResourceAccessRegistry resourceAccessRegistry;

    @Override
    public Set<ResourceReference> readable(Collection<ResourceReference> references) {
        return resourceAccessRegistry.readable(references);
    }

    @Override
    public Set<ResourceReference> referenceable(Collection<ResourceReference> references) {
        return resourceAccessRegistry.referenceable(references);
    }

    @Override
    public Map<ResourceReference, ResourceRefDTO> loadReadable(
            Collection<ResourceReference> references) {
        return resourceAccessRegistry.loadReadable(references);
    }
}
