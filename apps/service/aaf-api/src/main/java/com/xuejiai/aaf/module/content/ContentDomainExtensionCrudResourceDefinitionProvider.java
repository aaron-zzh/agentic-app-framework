package com.xuejiai.aaf.module.content;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceEndpointBinding;
import com.xuejiai.aaf.module.content.controller.ContentDomainExtensionController;
import com.xuejiai.aaf.module.content.domain.ContentDomainExtension;

/**
 * 行业扩展资源契约 Provider。
 *
 * @author AaronZZH & Kiro
 */
@Component
public final class ContentDomainExtensionCrudResourceDefinitionProvider
        implements CrudResourceDefinitionProvider<ContentDomainExtension> {

    private static final CrudResourceEndpointBinding ENDPOINT_BINDING =
            CrudResourceEndpointBinding.crud(
                    ContentDomainExtensionResource.KEY,
                    ContentDomainExtensionController.class,
                    ContentDomainExtensionResource.BASE_PATH);

    @Override
    public CrudResourceDefinition<ContentDomainExtension> definition() {
        return ContentDomainExtensionResource.DEFINITION;
    }

    @Override
    public CrudResourceEndpointBinding endpointBinding() {
        return ENDPOINT_BINDING;
    }
}
