package com.xuejiai.aaf.module.content;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceEndpointBinding;
import com.xuejiai.aaf.module.content.controller.ContentSnippetController;
import com.xuejiai.aaf.module.content.domain.ContentSnippet;

/**
 * 创作片段资源契约 Provider。
 *
 * @author AaronZZH & Kiro
 */
@Component
public final class ContentSnippetCrudResourceDefinitionProvider
        implements CrudResourceDefinitionProvider<ContentSnippet> {

    private static final CrudResourceEndpointBinding ENDPOINT_BINDING =
            CrudResourceEndpointBinding.crud(
                    ContentSnippetResource.KEY,
                    ContentSnippetController.class,
                    ContentSnippetResource.BASE_PATH);

    @Override
    public CrudResourceDefinition<ContentSnippet> definition() {
        return ContentSnippetResource.DEFINITION;
    }

    @Override
    public CrudResourceEndpointBinding endpointBinding() {
        return ENDPOINT_BINDING;
    }
}
