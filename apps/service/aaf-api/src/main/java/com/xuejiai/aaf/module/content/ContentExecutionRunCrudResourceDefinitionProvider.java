package com.xuejiai.aaf.module.content;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceEndpointBinding;
import com.xuejiai.aaf.module.content.controller.ContentExecutionRunController;
import com.xuejiai.aaf.module.content.domain.ContentExecutionRun;

/**
 * 执行记录资源契约 Provider。
 *
 * @author AaronZZH & Kiro
 */
@Component
public final class ContentExecutionRunCrudResourceDefinitionProvider
        implements CrudResourceDefinitionProvider<ContentExecutionRun> {

    private static final CrudResourceEndpointBinding ENDPOINT_BINDING =
            CrudResourceEndpointBinding.crud(
                    ContentExecutionRunResource.KEY,
                    ContentExecutionRunController.class,
                    ContentExecutionRunResource.BASE_PATH);

    @Override
    public CrudResourceDefinition<ContentExecutionRun> definition() {
        return ContentExecutionRunResource.DEFINITION;
    }

    @Override
    public CrudResourceEndpointBinding endpointBinding() {
        return ENDPOINT_BINDING;
    }
}
