package com.xuejiai.aaf.module.content;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceEndpointBinding;
import com.xuejiai.aaf.module.content.controller.ContentObjectVersionController;
import com.xuejiai.aaf.module.content.domain.ContentObjectVersion;

/**
 * 内容对象版本资源契约 Provider。
 *
 * @author AaronZZH & Kiro
 */
@Component
public final class ContentObjectVersionCrudResourceDefinitionProvider
        implements CrudResourceDefinitionProvider<ContentObjectVersion> {

    private static final CrudResourceEndpointBinding ENDPOINT_BINDING =
            CrudResourceEndpointBinding.crud(
                    ContentObjectVersionResource.KEY,
                    ContentObjectVersionController.class,
                    ContentObjectVersionResource.BASE_PATH);

    @Override
    public CrudResourceDefinition<ContentObjectVersion> definition() {
        return ContentObjectVersionResource.DEFINITION;
    }

    @Override
    public CrudResourceEndpointBinding endpointBinding() {
        return ENDPOINT_BINDING;
    }
}
