package com.xuejiai.aaf.module.ai.aigc.project.resource;

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
import com.xuejiai.aaf.module.ai.aigc.project.controller.AigcProjectController;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;

/** 唯一 AIGC Project 管理根资源。 */
public final class AigcProjectResource {

    public static final ResourceKey KEY = ResourceKey.of("aigc.project");
    public static final String BASE_PATH = "/api/aigc/projects";

    private static final CrudResourceTypeContract<AigcProject> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    AigcProjectController.class, AigcProject.class);

    public static final CrudResourceDefinition<AigcProject> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("AIGC 项目", BASE_PATH, "aigc:project"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.CREATE,
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of(
                                    "name",
                                    "description",
                                    "projectTypeCode",
                                    "productionMode",
                                    "generationMode",
                                    "status",
                                    "brief",
                                    "prompt",
                                    "coverMediaVersionId",
                                    "configSnapshotId",
                                    "graphRevision",
                                    "primaryBrandProfileId",
                                    "assistantId",
                                    "budgetLimit",
                                    "costUsed",
                                    "lastActiveTime"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.byProperty("ownerId"));

    private AigcProjectResource() {}
}
