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
import com.xuejiai.aaf.module.ai.aigc.configuration.controller.AigcProjectTypePackageController;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcProjectTypePackage;

/** AIGC 项目类型兼容包 CRUD 资源定义。 */
public final class AigcProjectTypePackageResource {

    public static final ResourceKey KEY = ResourceKey.of("aigc.project-type-package");
    public static final String BASE_PATH = "/api/aigc/project-type-packages";
    public static final String COMMAND_PUBLISH = "PUBLISH";

    private static final CrudResourceTypeContract<AigcProjectTypePackage> TYPES =
            CrudResourceTypeContract.fromCrudController(
                    AigcProjectTypePackageController.class, AigcProjectTypePackage.class);

    public static final CrudResourceDefinition<AigcProjectTypePackage> DEFINITION =
            CrudResourceDefinition.standard(
                    KEY,
                    TYPES,
                    new CrudResourceDescriptor("项目类型兼容包", BASE_PATH, "aigc:project-type-package"),
                    CrudCapabilityDefinition.forTypes(TYPES)
                            .without(
                                    CrudOperation.DELETE_BATCH,
                                    CrudOperation.IMPORT,
                                    CrudOperation.RESTORE,
                                    CrudOperation.ARCHIVE),
                    new CrudQueryDefinition<>(
                            CrudFilterSchema.auto(),
                            Set.of(
                                    "packageVersion",
                                    "projectTypeId",
                                    "blueprintId",
                                    "domainExtensionId",
                                    "productionMode",
                                    "status"),
                            Sort.by("id").descending()),
                    CrudMutationDefinition.forTypes(TYPES)
                            .withCustomUpdateCommands(
                                    java.util.Map.of(
                                            COMMAND_PUBLISH,
                                            Set.of("status", "compatibilityResult"))),
                    CrudViewDefinition.forTypes(TYPES),
                    TenantScope.ORG_SHARED_WORKSPACE_OPTIONAL,
                    PersonalScope.none());

    private AigcProjectTypePackageResource() {}
}
