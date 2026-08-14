package com.xuejiai.aaf.module.system.task;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Sort;

import com.xuejiai.aaf.framework.crud.definition.CrudCapabilityDefinition;
import com.xuejiai.aaf.framework.crud.definition.CrudMutationDefinition;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.definition.CrudQueryDefinition;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceDescriptor;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceExposure;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceTypeContract;
import com.xuejiai.aaf.framework.crud.definition.CrudViewDefinition;
import com.xuejiai.aaf.framework.crud.definition.PersonalScope;
import com.xuejiai.aaf.framework.crud.definition.ResourceKey;
import com.xuejiai.aaf.framework.crud.definition.TenantScope;
import com.xuejiai.aaf.framework.crud.filter.CrudFilterSchema;
import com.xuejiai.aaf.framework.task.TaskExecution;
import com.xuejiai.aaf.module.system.task.controller.TaskExecutionEntityController;

/** 任务执行审计只读资源定义。 */
public final class TaskExecutionResource {

    public static final ResourceKey KEY = ResourceKey.of("system.task-execution");
    public static final String BASE_PATH = "/api/task-records/executions";

    private static final CrudResourceTypeContract<TaskExecution> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    TaskExecutionEntityController.class, TaskExecution.class);

    private static final CrudViewDefinition VIEW =
            CrudViewDefinition.forTypes(TYPES)
                    .withFieldSet(
                            "list",
                            Set.of(
                                    "id",
                                    "taskName",
                                    "taskType",
                                    "status",
                                    "startTime",
                                    "endTime",
                                    "durationMs",
                                    "retryCount",
                                    "priority",
                                    "triggerType",
                                    "bizId",
                                    "createTime"))
                    .withFieldSet(
                            "detail",
                            Set.of(
                                    "id",
                                    "taskName",
                                    "taskType",
                                    "status",
                                    "startTime",
                                    "endTime",
                                    "durationMs",
                                    "errorMessage",
                                    "retryCount",
                                    "priority",
                                    "triggerType",
                                    "bizId",
                                    "context",
                                    "orgId",
                                    "workspaceId",
                                    "createTime"));

    public static final CrudResourceDefinition<TaskExecution> DEFINITION =
            new CrudResourceDefinition<>(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("任务执行审计", BASE_PATH, "system:task-execution"),
                    new CrudCapabilityDefinition(
                            List.of(
                                    CrudOperation.PAGE,
                                    CrudOperation.QUERY,
                                    CrudOperation.GET,
                                    CrudOperation.BATCH_READ,
                                    CrudOperation.META)),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of("id", "taskName", "taskType", "status", "bizId", "startTime"),
                            Sort.by("startTime").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    VIEW,
                    List.of(),
                    TenantScope.GLOBAL,
                    PersonalScope.none(),
                    Set.of(CrudResourceExposure.HTTP, CrudResourceExposure.ENTITY_DEF),
                    CrudResourceDefinition.CURRENT_SCHEMA_VERSION);

    private TaskExecutionResource() {}
}
