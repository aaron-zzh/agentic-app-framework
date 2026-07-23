package com.xuejiai.aaf.framework.crud.resource;

import java.util.Objects;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;

/** 显式绑定 Definition 与端点的不可变 Provider。 */
public final class StaticCrudResourceDefinitionProvider<E extends BaseEntity>
        implements CrudResourceDefinitionProvider<E> {

    private final CrudResourceDefinition<E> definition;
    private final CrudResourceEndpointBinding endpointBinding;

    public StaticCrudResourceDefinitionProvider(
            CrudResourceDefinition<E> definition, CrudResourceEndpointBinding endpointBinding) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.endpointBinding = Objects.requireNonNull(endpointBinding, "endpointBinding");
    }

    @Override
    public CrudResourceDefinition<E> definition() {
        return definition;
    }

    @Override
    public CrudResourceEndpointBinding endpointBinding() {
        return endpointBinding;
    }
}
