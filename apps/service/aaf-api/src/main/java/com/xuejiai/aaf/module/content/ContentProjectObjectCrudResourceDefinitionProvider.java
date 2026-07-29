package com.xuejiai.aaf.module.content;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceEndpointBinding;
import com.xuejiai.aaf.module.content.controller.ContentProjectObjectController;
import com.xuejiai.aaf.module.content.domain.ContentProjectObject;

/**
 * 项目对象资源契约 Provider。
 *
 * @author AaronZZH & Kiro
 */
@Component
public final class ContentProjectObjectCrudResourceDefinitionProvider
        implements CrudResourceDefinitionProvider<ContentProjectObject> {

    private static final CrudResourceEndpointBinding ENDPOINT_BINDING =
            CrudResourceEndpointBinding.crud(
                    ContentProjectObjectResource.KEY,
                    ContentProjectObjectController.class,
                    ContentProjectObjectResource.BASE_PATH);

    @Override
    public CrudResourceDefinition<ContentProjectObject> definition() {
        return ContentProjectObjectResource.DEFINITION;
    }

    @Override
    public CrudResourceEndpointBinding endpointBinding() {
        return ENDPOINT_BINDING;
    }
}
