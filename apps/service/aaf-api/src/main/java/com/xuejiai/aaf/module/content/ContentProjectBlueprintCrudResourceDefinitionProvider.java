package com.xuejiai.aaf.module.content;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceEndpointBinding;
import com.xuejiai.aaf.module.content.controller.ContentProjectBlueprintController;
import com.xuejiai.aaf.module.content.domain.ContentProjectBlueprint;

/**
 * 项目蓝图资源契约 Provider。
 *
 * @author AaronZZH & Kiro
 */
@Component
public final class ContentProjectBlueprintCrudResourceDefinitionProvider
        implements CrudResourceDefinitionProvider<ContentProjectBlueprint> {

    private static final CrudResourceEndpointBinding ENDPOINT_BINDING =
            CrudResourceEndpointBinding.crud(
                    ContentProjectBlueprintResource.KEY,
                    ContentProjectBlueprintController.class,
                    ContentProjectBlueprintResource.BASE_PATH);

    @Override
    public CrudResourceDefinition<ContentProjectBlueprint> definition() {
        return ContentProjectBlueprintResource.DEFINITION;
    }

    @Override
    public CrudResourceEndpointBinding endpointBinding() {
        return ENDPOINT_BINDING;
    }
}
