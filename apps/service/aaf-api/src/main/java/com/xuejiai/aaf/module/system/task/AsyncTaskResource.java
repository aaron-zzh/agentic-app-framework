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
import com.xuejiai.aaf.framework.task.AsyncTask;
import com.xuejiai.aaf.module.system.task.controller.AsyncTaskEntityController;

/** 异步任务只读资源定义。 */
public final class AsyncTaskResource {

    public static final ResourceKey KEY = ResourceKey.of("system.async-task");
    public static final String BASE_PATH = "/api/task-records/async";

    private static final CrudResourceTypeContract<AsyncTask> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    AsyncTaskEntityController.class, AsyncTask.class);

    private static final CrudViewDefinition VIEW =
            CrudViewDefinition.forTypes(TYPES)
                    .withFieldSet(
                            "list",
                            Set.of(
                                    "id",
                                    "taskId",
                                    "taskType",
                                    "status",
                                    "priority",
                                    "attemptCount",
                                    "maxRetries",
                                    "startedAt",
                                    "completedAt",
                                    "createTime",
                                    "updateTime"))
                    .withFieldSet(
                            "detail",
                            Set.of(
                                    "id",
                                    "taskId",
                                    "taskType",
                                    "status",
                                    "priority",
                                    "attemptCount",
                                    "maxRetries",
                                    "result",
                                    "lastError",
                                    "ownerId",
                                    "orgId",
                                    "workspaceId",
                                    "startedAt",
                                    "completedAt",
                                    "createTime",
                                    "updateTime"));

    public static final CrudResourceDefinition<AsyncTask> DEFINITION =
            new CrudResourceDefinition<>(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("异步任务", BASE_PATH, "system:async-task"),
                    new CrudCapabilityDefinition(
                            List.of(
                                    CrudOperation.PAGE,
                                    CrudOperation.QUERY,
                                    CrudOperation.GET,
                                    CrudOperation.BATCH_READ,
                                    CrudOperation.META)),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of("id", "taskId", "taskType", "status", "createTime"),
                            Sort.by("createTime").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    VIEW,
                    List.of(),
                    TenantScope.WORKSPACE_REQUIRED,
                    PersonalScope.none(),
                    Set.of(CrudResourceExposure.HTTP, CrudResourceExposure.ENTITY_DEF),
                    CrudResourceDefinition.CURRENT_SCHEMA_VERSION);

    private AsyncTaskResource() {}
}
