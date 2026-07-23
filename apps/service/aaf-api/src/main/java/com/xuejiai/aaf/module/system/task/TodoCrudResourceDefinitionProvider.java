package com.xuejiai.aaf.module.system.task;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceEndpointBinding;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.module.system.task.controller.TodoController;
import com.xuejiai.aaf.module.system.task.domain.Todo;

/** Todo 的显式资源契约 Provider。 */
@Component
public final class TodoCrudResourceDefinitionProvider
        implements CrudResourceDefinitionProvider<Todo> {

    private static final CrudResourceEndpointBinding ENDPOINT_BINDING =
            CrudResourceEndpointBinding.crud(TodoResource.KEY, TodoController.class, "/api/todos");

    @Override
    public CrudResourceDefinition<Todo> definition() {
        return TodoResource.DEFINITION;
    }

    @Override
    public CrudResourceEndpointBinding endpointBinding() {
        return ENDPOINT_BINDING;
    }
}
