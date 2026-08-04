package com.xuejiai.aaf.module.ai.aigc.execution.resource;

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
import com.xuejiai.aaf.module.ai.aigc.execution.controller.AigcExecutionRunController;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionRun;

public final class AigcExecutionRunResource {

    public static final ResourceKey KEY = ResourceKey.of("aigc.execution-run");
    public static final String BASE_PATH = "/api/aigc/execution-runs";

    private static final CrudResourceTypeContract<AigcExecutionRun> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    AigcExecutionRunController.class, AigcExecutionRun.class);

    public static final CrudResourceDefinition<AigcExecutionRun> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("AIGC 执行记录", BASE_PATH, "aigc:execution-run"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.CREATE,
                                    CrudOperation.UPDATE,
                                    CrudOperation.DELETE,
                                    CrudOperation.DELETE_BATCH,
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of(
                                    "projectId",
                                    "objectId",
                                    "actionKey",
                                    "targetType",
                                    "targetRef",
                                    "status",
                                    "generationMode",
                                    "roleProfileCode",
                                    "selectedModelVersion",
                                    "costCredits",
                                    "retryCount",
                                    "errorMessage",
                                    "startTime",
                                    "endTime"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.byProperty("ownerId"));

    private AigcExecutionRunResource() {}
}
