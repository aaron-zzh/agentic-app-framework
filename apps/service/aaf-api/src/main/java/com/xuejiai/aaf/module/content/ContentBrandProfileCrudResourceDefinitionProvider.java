package com.xuejiai.aaf.module.content;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceEndpointBinding;
import com.xuejiai.aaf.module.content.controller.ContentBrandProfileController;
import com.xuejiai.aaf.module.content.domain.ContentBrandProfile;

/**
 * 品牌/IP 资料资源契约 Provider。
 *
 * @author AaronZZH & Kiro
 */
@Component
public final class ContentBrandProfileCrudResourceDefinitionProvider
        implements CrudResourceDefinitionProvider<ContentBrandProfile> {

    private static final CrudResourceEndpointBinding ENDPOINT_BINDING =
            CrudResourceEndpointBinding.crud(
                    ContentBrandProfileResource.KEY,
                    ContentBrandProfileController.class,
                    ContentBrandProfileResource.BASE_PATH);

    @Override
    public CrudResourceDefinition<ContentBrandProfile> definition() {
        return ContentBrandProfileResource.DEFINITION;
    }

    @Override
    public CrudResourceEndpointBinding endpointBinding() {
        return ENDPOINT_BINDING;
    }
}
