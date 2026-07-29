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
import com.xuejiai.aaf.module.content.controller.ContentProjectBlueprintController;
import com.xuejiai.aaf.module.content.domain.ContentProjectBlueprint;

/**
 * 项目蓝图 CRUD 资源定义。
 *
 * @author AaronZZH & Kiro
 */
public final class ContentProjectBlueprintResource {

    public static final ResourceKey KEY = ResourceKey.of("content.blueprint");
    public static final String BASE_PATH = "/api/content/blueprints";

    private static final CrudResourceTypeContract<ContentProjectBlueprint> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    ContentProjectBlueprintController.class, ContentProjectBlueprint.class);

    public static final CrudResourceDefinition<ContentProjectBlueprint> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("项目蓝图", BASE_PATH, "content:blueprint"),
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
                                    "code",
                                    "name",
                                    "projectTypeCode",
                                    "blueprintVersion",
                                    "productionMode",
                                    "description",
                                    "status"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES),
                    CrudViewDefinition.forTypes(TYPES)
                            .withFieldSet(
                                    "list",
                                    Set.of(
                                            "id",
                                            "code",
                                            "name",
                                            "projectTypeCode",
                                            "blueprintVersion",
                                            "productionMode",
                                            "description",
                                            "status")),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.none());

    private ContentProjectBlueprintResource() {}
}
