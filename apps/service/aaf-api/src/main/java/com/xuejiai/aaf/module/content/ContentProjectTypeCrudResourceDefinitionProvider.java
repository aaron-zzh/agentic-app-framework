package com.xuejiai.aaf.module.content;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceEndpointBinding;
import com.xuejiai.aaf.module.content.controller.ContentProjectTypeController;
import com.xuejiai.aaf.module.content.domain.ContentProjectType;

/**
 * 项目类型资源契约 Provider。
 *
 * @author AaronZZH & Kiro
 */
@Component
public final class ContentProjectTypeCrudResourceDefinitionProvider
        implements CrudResourceDefinitionProvider<ContentProjectType> {

    private static final CrudResourceEndpointBinding ENDPOINT_BINDING =
            CrudResourceEndpointBinding.crud(
                    ContentProjectTypeResource.KEY,
                    ContentProjectTypeController.class,
                    ContentProjectTypeResource.BASE_PATH);

    @Override
    public CrudResourceDefinition<ContentProjectType> definition() {
        return ContentProjectTypeResource.DEFINITION;
    }

    @Override
    public CrudResourceEndpointBinding endpointBinding() {
        return ENDPOINT_BINDING;
    }
}
