package com.xuejiai.aaf.module.ai.aigc.task.resource;

import java.util.List;
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
import com.xuejiai.aaf.module.ai.aigc.task.controller.AigcTaskController;
import com.xuejiai.aaf.module.ai.aigc.task.domain.AigcTask;

/** AIGC 媒体生成任务只读资源定义。 */
public final class AigcTaskResource {

    public static final ResourceKey KEY = ResourceKey.of("aigc.task");
    public static final String BASE_PATH = "/api/aigc/tasks";

    private static final CrudResourceTypeContract<AigcTask> TYPES =
            CrudResourceTypeContract.fromCrudController(AigcTaskController.class, AigcTask.class);

    public static final CrudResourceDefinition<AigcTask> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("AIGC 任务", BASE_PATH, "aigc:task"),
                    new CrudCapabilityDefinition(List.of(CrudOperation.PAGE, CrudOperation.GET)),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of(
                                    "id",
                                    "type",
                                    "status",
                                    "provider",
                                    "model",
                                    "projectId",
                                    "createTime"),
                            Sort.by("createTime").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.byProperty("userId"));

    private AigcTaskResource() {}
}
