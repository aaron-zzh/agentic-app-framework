package com.xuejiai.aaf.module.content;

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
import com.xuejiai.aaf.module.content.controller.ContentExecutionRunController;
import com.xuejiai.aaf.module.content.domain.ContentExecutionRun;

/**
 * 执行记录 CRUD 资源定义。
 *
 * @author AaronZZH & Kiro
 */
public final class ContentExecutionRunResource {

    public static final ResourceKey KEY = ResourceKey.of("content.execution-run");
    public static final String BASE_PATH = "/api/content/execution-runs";

    private static final CrudResourceTypeContract<ContentExecutionRun> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    ContentExecutionRunController.class, ContentExecutionRun.class);

    public static final CrudResourceDefinition<ContentExecutionRun> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("执行记录", BASE_PATH, "content:execution-run"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE,
                                    CrudOperation.CREATE,
                                    CrudOperation.UPDATE,
                                    CrudOperation.DELETE,
                                    CrudOperation.DELETE_BATCH),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.empty(),
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
                                    "errorMessage",
                                    "startTime",
                                    "endTime"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES)
                            .withFieldSet(
                                    "list",
                                    Set.of(
                                            "id",
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
                                            "errorMessage",
                                            "startTime",
                                            "endTime",
                                            "createTime")),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.byProperty("ownerId"));

    private ContentExecutionRunResource() {}
}
