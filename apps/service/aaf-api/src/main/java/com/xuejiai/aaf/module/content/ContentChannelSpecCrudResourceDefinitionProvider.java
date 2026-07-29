package com.xuejiai.aaf.module.content;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceEndpointBinding;
import com.xuejiai.aaf.module.content.controller.ContentChannelSpecController;
import com.xuejiai.aaf.module.content.domain.ContentChannelSpec;

/**
 * 渠道规格资源契约 Provider。
 *
 * @author AaronZZH & Kiro
 */
@Component
public final class ContentChannelSpecCrudResourceDefinitionProvider
        implements CrudResourceDefinitionProvider<ContentChannelSpec> {

    private static final CrudResourceEndpointBinding ENDPOINT_BINDING =
            CrudResourceEndpointBinding.crud(
                    ContentChannelSpecResource.KEY,
                    ContentChannelSpecController.class,
                    ContentChannelSpecResource.BASE_PATH);

    @Override
    public CrudResourceDefinition<ContentChannelSpec> definition() {
        return ContentChannelSpecResource.DEFINITION;
    }

    @Override
    public CrudResourceEndpointBinding endpointBinding() {
        return ENDPOINT_BINDING;
    }
}
