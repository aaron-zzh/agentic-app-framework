package com.xuejiai.aaf.module.system.task;

import java.util.Set;

import org.springframework.data.domain.Sort;

import com.xuejiai.aaf.framework.crud.definition.CrudCapabilityDefinition;
import com.xuejiai.aaf.framework.crud.definition.CrudMutationDefinition;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.definition.CrudQueryDefinition;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceDescriptor;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceTypeContract;
import com.xuejiai.aaf.framework.crud.definition.CrudViewDefinition;
import com.xuejiai.aaf.framework.crud.definition.PersonalScope;
import com.xuejiai.aaf.framework.crud.definition.ResourceKey;
import com.xuejiai.aaf.framework.crud.definition.TenantScope;
import com.xuejiai.aaf.framework.crud.filter.CrudFilterSchema;
import com.xuejiai.aaf.module.system.task.controller.TodoController;
import com.xuejiai.aaf.module.system.task.domain.Todo;

/** Todo CRUD 资源定义。 */
public final class TodoResource {

    public static final ResourceKey KEY = ResourceKey.of("system.todo");
    public static final String BASE_PATH = "/api/todos";
    public static final String COMMAND_SHARE = "TODO_SHARE";

    private static final CrudResourceTypeContract<Todo> TYPES =
            CrudResourceTypeContract.fromCrudController(TodoController.class, Todo.class);

    private static final CrudViewDefinition VIEW =
            CrudViewDefinition.forTypes(TYPES)
                    .withFieldSet(
                            "list",
                            Set.of(
                                    "id",
                                    "version",
                                    "title",
                                    "category",
                                    "status",
                                    "dueDate",
                                    "createTime",
                                    "assignee",
                                    "participants"))
                    .withFieldSet("picker", Set.of("id", "title"))
                    .withFieldSet(
                            "export",
                            Set.of(
                                    "id",
                                    "version",
                                    "assigneeId",
                                    "title",
                                    "category",
                                    "sourceType",
                                    "source",
                                    "status",
                                    "dueDate",
                                    "createTime"));

    public static final CrudResourceDefinition<Todo> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("待办", BASE_PATH, "system:todo"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of("id", "title", "dueDate"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES)
                            .withCustomUpdateCommands(
                                    java.util.Map.of(COMMAND_SHARE, Set.of("participants"))),
                    VIEW,
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.byProperty("assigneeId"));

    private TodoResource() {}
}
