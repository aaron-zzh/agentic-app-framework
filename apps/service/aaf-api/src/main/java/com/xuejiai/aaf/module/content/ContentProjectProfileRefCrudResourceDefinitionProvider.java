package com.xuejiai.aaf.module.content;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceEndpointBinding;
import com.xuejiai.aaf.module.content.controller.ContentProjectProfileRefController;
import com.xuejiai.aaf.module.content.domain.ContentProjectProfileRef;

/**
 * 项目资料引用资源契约 Provider。
 *
 * @author AaronZZH & Kiro
 */
@Component
public final class ContentProjectProfileRefCrudResourceDefinitionProvider
        implements CrudResourceDefinitionProvider<ContentProjectProfileRef> {

    private static final CrudResourceEndpointBinding ENDPOINT_BINDING =
            CrudResourceEndpointBinding.crud(
                    ContentProjectProfileRefResource.KEY,
                    ContentProjectProfileRefController.class,
                    ContentProjectProfileRefResource.BASE_PATH);

    @Override
    public CrudResourceDefinition<ContentProjectProfileRef> definition() {
        return ContentProjectProfileRefResource.DEFINITION;
    }

    @Override
    public CrudResourceEndpointBinding endpointBinding() {
        return ENDPOINT_BINDING;
    }
}
