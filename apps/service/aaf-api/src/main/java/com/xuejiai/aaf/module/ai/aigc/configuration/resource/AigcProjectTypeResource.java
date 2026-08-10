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
import com.xuejiai.aaf.module.ai.aigc.configuration.controller.AigcProjectTypeController;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcProjectType;

/** AIGC 项目类型 CRUD 资源定义。 */
public final class AigcProjectTypeResource {

    public static final ResourceKey KEY = ResourceKey.of("aigc.project-type");
    public static final String BASE_PATH = "/api/aigc/project-types";
    public static final String COMMAND_PUBLISH = "PUBLISH";

    private static final CrudResourceTypeContract<AigcProjectType> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    AigcProjectTypeController.class, AigcProjectType.class);

    public static final CrudResourceDefinition<AigcProjectType> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("项目类型", BASE_PATH, "aigc:project-type"),
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
                                    "definitionVersion",
                                    "defaultProductionMode",
                                    "quickEntry",
                                    "builtin",
                                    "sortOrder",
                                    "status"),
                            Sort.by("sortOrder").ascending()),
                    CrudMutationDefinition.forTypes(TYPES)
                            .withCustomUpdateCommands(
                                    java.util.Map.of(COMMAND_PUBLISH, Set.of("status"))),
                    CrudViewDefinition.forTypes(TYPES),
                    TenantScope.GLOBAL,
                    PersonalScope.none());

    private AigcProjectTypeResource() {}
}
