package com.xuejiai.aaf.framework.crud.resource;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.xuejiai.aaf.framework.crud.definition.*;
import com.xuejiai.aaf.framework.crud.reference.CrudReferenceDefinition;

/** 启动期编译后供 EntityDef、引用目录、CRUD 元数据与 AI 消费的资源快照。 */
public record CrudResourceSnapshot(
        ResourceKey key,
        String slug,
        CrudResourceDescriptor descriptor,
        List<String> operations,
        List<String> fieldSets,
        TenantScope tenantScope,
        Set<CrudResourceExposure> exposures,
        int schemaVersion,
        List<String> fields,
        List<CrudReferenceDefinition> references,
        boolean referenceable,
        String fingerprint,
        Instant builtAt) {

    public CrudResourceSnapshot {
        operations =
                operations.stream()
                        .map(CrudOperation::fromCapability)
                        .distinct()
                        .map(CrudOperation::capability)
                        .toList();
        fieldSets = List.copyOf(fieldSets);
        exposures = Set.copyOf(exposures);
        fields = List.copyOf(fields);
        references = List.copyOf(references);
    }
}
