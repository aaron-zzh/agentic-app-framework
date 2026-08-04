package com.xuejiai.aaf.module.ai.aigc.work.resource;

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
import com.xuejiai.aaf.module.ai.aigc.work.controller.AigcWorkController;
import com.xuejiai.aaf.module.ai.aigc.work.domain.AigcWork;

public final class AigcWorkResource {

    public static final ResourceKey KEY = ResourceKey.of("aigc.work");
    public static final String BASE_PATH = "/api/aigc/works";

    private static final CrudResourceTypeContract<AigcWork> TYPES =
            CrudResourceTypeContract.fromCrudController(AigcWorkController.class, AigcWork.class);

    public static final CrudResourceDefinition<AigcWork> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("AIGC 作品", BASE_PATH, "aigc:work"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.CREATE,
                                    CrudOperation.DELETE,
                                    CrudOperation.DELETE_BATCH,
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of(
                                    "projectId",
                                    "deliverableObjectId",
                                    "adoptedObjectVersionId",
                                    "title",
                                    "coverMediaVersionId",
                                    "status",
                                    "visibility",
                                    "userId"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.byProperty("userId"));

    private AigcWorkResource() {}
}
