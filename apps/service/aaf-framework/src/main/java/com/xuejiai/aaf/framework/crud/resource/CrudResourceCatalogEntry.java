package com.xuejiai.aaf.framework.crud.resource;

import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceTypeContract;
import com.xuejiai.aaf.framework.crud.definition.ResourceKey;
import com.xuejiai.aaf.framework.crud.enforcement.CompiledFieldPolicy;
import com.xuejiai.aaf.framework.crud.view.CrudViewPlan;

/** 启动期已校验的资源契约与端点绑定。 */
public record CrudResourceCatalogEntry(
        CrudResourceDefinition<?> definition,
        CrudResourceEndpointBinding endpointBinding,
        CrudResourceSnapshot snapshot,
        CompiledFieldPolicy fieldPolicy,
        Map<String, CrudViewPlan> viewPlans) {

    public CrudResourceCatalogEntry {
        definition = Objects.requireNonNull(definition, "definition");
        endpointBinding = Objects.requireNonNull(endpointBinding, "endpointBinding");
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        fieldPolicy = Objects.requireNonNull(fieldPolicy, "fieldPolicy");
        viewPlans = Map.copyOf(Objects.requireNonNull(viewPlans, "viewPlans"));
        if (!definition.key().equals(endpointBinding.resourceKey())
                || !definition.key().equals(snapshot.key())) {
            throw new IllegalArgumentException("Definition、端点绑定与快照的 ResourceKey 不一致");
        }
    }

    public ResourceKey key() {
        return definition.key();
    }

    public CrudResourceTypeContract<?> types() {
        return definition.types();
    }

    public Class<? extends BaseEntity> entityType() {
        return types().entityType();
    }
}
