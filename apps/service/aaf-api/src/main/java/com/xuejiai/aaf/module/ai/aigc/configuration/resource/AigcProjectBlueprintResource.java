package com.xuejiai.aaf.module.ai.aigc.configuration.resource;

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
import com.xuejiai.aaf.module.ai.aigc.configuration.controller.AigcProjectBlueprintController;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcProjectBlueprint;

/** AIGC 项目蓝图 CRUD 资源定义。 */
public final class AigcProjectBlueprintResource {

    public static final ResourceKey KEY = ResourceKey.of("aigc.blueprint");
    public static final String BASE_PATH = "/api/aigc/project-blueprints";

    private static final CrudResourceTypeContract<AigcProjectBlueprint> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    AigcProjectBlueprintController.class, AigcProjectBlueprint.class);

    public static final CrudResourceDefinition<AigcProjectBlueprint> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("项目蓝图", BASE_PATH, "aigc:blueprint"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.DELETE_BATCH,
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of(
                                    "code",
                                    "name",
                                    "projectTypeCode",
                                    "blueprintVersion",
                                    "productionMode",
                                    "description",
                                    "status"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES)
                            .withCustomUpdateCommands(
                                    java.util.Map.of("PUBLISH", Set.of("status"))),
                    CrudViewDefinition.forTypes(TYPES),
                    TenantScope.GLOBAL,
                    PersonalScope.none());

    private AigcProjectBlueprintResource() {}
}
