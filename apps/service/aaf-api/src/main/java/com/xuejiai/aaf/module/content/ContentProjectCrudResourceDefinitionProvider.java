package com.xuejiai.aaf.module.content;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceEndpointBinding;
import com.xuejiai.aaf.module.content.controller.ContentProjectController;
import com.xuejiai.aaf.module.content.domain.ContentProject;

/**
 * 内容项目资源契约 Provider。
 *
 * @author AaronZZH & Kiro
 */
@Component
public final class ContentProjectCrudResourceDefinitionProvider
        implements CrudResourceDefinitionProvider<ContentProject> {

    private static final CrudResourceEndpointBinding ENDPOINT_BINDING =
            CrudResourceEndpointBinding.crud(
                    ContentProjectResource.KEY,
                    ContentProjectController.class,
                    ContentProjectResource.BASE_PATH);

    @Override
    public CrudResourceDefinition<ContentProject> definition() {
        return ContentProjectResource.DEFINITION;
    }

    @Override
    public CrudResourceEndpointBinding endpointBinding() {
        return ENDPOINT_BINDING;
    }
}
