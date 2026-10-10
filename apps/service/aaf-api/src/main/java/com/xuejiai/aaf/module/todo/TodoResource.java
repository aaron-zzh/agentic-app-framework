package com.xuejiai.aaf.module.todo;

import java.util.Map;
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
import com.xuejiai.aaf.module.todo.controller.TodoController;
import com.xuejiai.aaf.module.todo.domain.Todo;

/**
 * Todo CRUD 资源定义：向框架声明本资源的路径、能力、字段集、筛选排序与安全范围。
 *
 * <p>阅读顺序：资源键与路径 → 类型契约（从 Controller 泛型推导）→ 输出视图字段集 → 能力开关 → 查询定义 → 变更定义（具名命令）→ 租户与个人范围。
 *
 * <ul>
 *   <li>资源键 {@code system.todo}、权限前缀 {@code system:todo}：与 seed 中的权限码、EntityDef 一一对应
 *   <li>字段集：list 供列表，picker 供选择器，export 供导出，未列出的字段不会输出
 *   <li>个人范围：默认只能看到执行人是自己的待办，协作者通过 ReBAC 关系放宽读取
 * </ul>
 */
public final class TodoResource {

    public static final ResourceKey KEY = ResourceKey.of("system.todo");
    public static final String BASE_PATH = "/api/todos";

    /** 具名 UPDATE 命令：分享待办给协作者。 */
    public static final String COMMAND_SHARE = "TODO_SHARE";

    /** 具名 UPDATE 命令：撤销协作者的访问关系。 */
    public static final String COMMAND_UNSHARE = "TODO_UNSHARE";

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
                                    Map.of(
                                            COMMAND_SHARE, Set.of("participants"),
                                            COMMAND_UNSHARE, Set.of("participants"))),
                    VIEW,
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.byProperty("assigneeId"));

    private TodoResource() {}
}
