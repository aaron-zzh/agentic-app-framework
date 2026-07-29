package com.xuejiai.aaf.module.content;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceEndpointBinding;
import com.xuejiai.aaf.module.content.controller.ContentProjectRelationController;
import com.xuejiai.aaf.module.content.domain.ContentProjectRelation;

/**
 * 项目关系资源契约 Provider。
 *
 * @author AaronZZH & Kiro
 */
@Component
public final class ContentProjectRelationCrudResourceDefinitionProvider
        implements CrudResourceDefinitionProvider<ContentProjectRelation> {

    private static final CrudResourceEndpointBinding ENDPOINT_BINDING =
            CrudResourceEndpointBinding.crud(
                    ContentProjectRelationResource.KEY,
                    ContentProjectRelationController.class,
                    ContentProjectRelationResource.BASE_PATH);

    @Override
    public CrudResourceDefinition<ContentProjectRelation> definition() {
        return ContentProjectRelationResource.DEFINITION;
    }

    @Override
    public CrudResourceEndpointBinding endpointBinding() {
        return ENDPOINT_BINDING;
    }
}
